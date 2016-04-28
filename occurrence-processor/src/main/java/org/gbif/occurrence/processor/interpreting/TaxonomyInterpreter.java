package org.gbif.occurrence.processor.interpreting;

import org.gbif.api.model.checklistbank.NameUsageMatch;
import org.gbif.api.model.checklistbank.ParsedName;
import org.gbif.api.model.occurrence.Occurrence;
import org.gbif.api.model.occurrence.VerbatimOccurrence;
import org.gbif.api.vocabulary.Extension;
import org.gbif.api.vocabulary.OccurrenceIssue;
import org.gbif.api.vocabulary.Rank;
import org.gbif.common.parsers.RankParser;
import org.gbif.common.parsers.core.OccurrenceParseResult;
import org.gbif.common.parsers.core.ParseResult;
import org.gbif.common.parsers.utils.ClassificationUtils;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.NameParser;
import org.gbif.nameparser.UnparsableException;
import org.gbif.occurrence.processor.guice.ApiClientConfiguration;
import org.gbif.occurrence.processor.interpreting.util.RetryingWebserviceClient;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.core.MultivaluedMap;

import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Takes a VerbatimOccurrence and does nub lookup on its provided taxonomy, then writes the result to the passed in
 * Occurrence.
 */
public class TaxonomyInterpreter {

  private static final Logger LOG = LoggerFactory.getLogger(TaxonomyInterpreter.class);
  private static final NameParser parser = new NameParser();
  private static final RankParser RANK_PARSER = RankParser.getInstance();
  private static final String MATCH_PATH = "species/match";

  // The repetitive nature of our data encourages use of a light cache to reduce WS load
  private static final LoadingCache<WebResource, NameUsageMatch> CACHE =
    CacheBuilder.newBuilder()
      .maximumSize(10000)
      .expireAfterAccess(120, TimeUnit.MINUTES)
      .build(RetryingWebserviceClient.newInstance(NameUsageMatch.class, 5, 2000));


  private final WebResource MATCHING_WS;

  @Inject
  public TaxonomyInterpreter(WebResource apiBaseWs) {
    MATCHING_WS = apiBaseWs.path(MATCH_PATH);
  }

  public TaxonomyInterpreter(ApiClientConfiguration cfg) {
    this(cfg.newApiClient());
  }

  /**
   * Assembles the most complete scientific name based on full and individual name parts.
   * @param scientificName the full scientific name
   * @param genericName see GbifTerm.genericName
   * @param genus see DwcTerm.genus
   * @param specificEpithet see DwcTerm.specificEpithet
   * @param infraspecificEpithet see DwcTerm.infraspecificEpithet
   */
  public static String buildScientificName(String scientificName, String authorship, String genericName, String genus, String specificEpithet, String infraspecificEpithet) {
    String sciname = ClassificationUtils.clean(scientificName);
    if (sciname == null) {
      // handle case when the scientific name is null and only given as atomized fields: genus & speciesEpitheton
      ParsedName pn = new ParsedName();
      if (!StringUtils.isBlank(genericName)) {
        pn.setGenusOrAbove(genericName);
      } else {
        pn.setGenusOrAbove(genus);
      }
      pn.setSpecificEpithet(specificEpithet);
      pn.setInfraSpecificEpithet(infraspecificEpithet);
      sciname = pn.canonicalName();
    }
    return sciname;
  }

  private OccurrenceParseResult<NameUsageMatch> match(Map<Term, String> terms) {
    Rank rank = interpretRank(terms);
    return match(
        value(terms, DwcTerm.kingdom),
        value(terms, DwcTerm.phylum),
        value(terms, DwcTerm.class_),
        value(terms, DwcTerm.order),
        value(terms, DwcTerm.family),
        value(terms, DwcTerm.genus),
        value(terms, DwcTerm.scientificName),
        value(terms, DwcTerm.scientificNameAuthorship),
        value(terms, GbifTerm.genericName),
        value(terms, DwcTerm.specificEpithet),
        value(terms, DwcTerm.infraspecificEpithet),
        rank);
  }

  public OccurrenceParseResult<NameUsageMatch> match(String kingdom, String phylum, String clazz, String order, String family, String genus,
                                                     String scientificName, String authorship,
                                                     String genericName, String specificEpithet, String infraspecificEpithet, Rank rank) {

    genus = ClassificationUtils.clean(genus);
    genericName = ClassificationUtils.clean(genericName);
    specificEpithet = ClassificationUtils.cleanAuthor(specificEpithet);
    infraspecificEpithet = ClassificationUtils.cleanAuthor(infraspecificEpithet);

    final String sciname = buildScientificName(scientificName, authorship, genericName, genus, specificEpithet, infraspecificEpithet);

    OccurrenceParseResult<NameUsageMatch> result;
    MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl();
    queryParams.add("kingdom", ClassificationUtils.clean(kingdom));
    queryParams.add("phylum",  ClassificationUtils.clean(phylum));
    queryParams.add("class",   ClassificationUtils.clean(clazz));
    queryParams.add("order",   ClassificationUtils.clean(order));
    queryParams.add("family",  ClassificationUtils.clean(family));
    queryParams.add("genus",  genus);
    queryParams.add("name",   sciname);
    if (rank != null) {
      queryParams.add("rank", rank.name());
    }

    LOG.debug("Attempt to match name [{}]", sciname);
    WebResource res = MATCHING_WS.queryParams(queryParams);
    LOG.debug("WS call with: {}", res.getURI());
    try {
      NameUsageMatch lookup = CACHE.get(res);
      result = OccurrenceParseResult.success(ParseResult.CONFIDENCE.DEFINITE, lookup);
      switch (lookup.getMatchType()) {
        case NONE:
          result.addIssue(OccurrenceIssue.TAXON_MATCH_NONE);
          LOG.info("match for [{}] returned no match. Lookup note: [{}]", scientificName, lookup.getNote());
          break;
        case FUZZY:
          result.addIssue(OccurrenceIssue.TAXON_MATCH_FUZZY);
          LOG.debug("match for [{}] was fuzzy. Match note: [{}]", scientificName, lookup.getNote());
          break;
        case HIGHERRANK:
          result.addIssue(OccurrenceIssue.TAXON_MATCH_HIGHERRANK);
          LOG.debug("match for [{}] was to higher rank only. Match note: [{}]", scientificName, lookup.getNote());
          break;
      }
    } catch (Exception e) {
      // Log the error
      LOG.error("Failed WS call with: {}", res.getURI());
      result = OccurrenceParseResult.error(e);
    }

    return result;
  }

  private void applyMatch(Occurrence occ, NameUsageMatch match, Collection<OccurrenceIssue> issues) {
    occ.setTaxonKey(match.getUsageKey());
    occ.setScientificName(match.getScientificName());
    occ.setTaxonRank(match.getRank());

    // copy issues
    occ.getIssues().addAll(issues);

    // parse name into pieces - we dont get them from the nub lookup
    try {
      ParsedName pn = parser.parse(match.getScientificName(), match.getRank());
      occ.setGenericName(pn.getGenusOrAbove());
      occ.setSpecificEpithet(pn.getSpecificEpithet());
      occ.setInfraspecificEpithet(pn.getInfraSpecificEpithet());
    } catch (UnparsableException e) {
      if (e.type.isParsable()) {
        LOG.warn("Fail to parse backbone {} name for occurrence {}: {}", e.type, occ.getKey(), e.name);
      }
    }

    for (Rank r : Rank.DWC_RANKS) {
      org.gbif.api.util.ClassificationUtils.setHigherRank(occ, r, match.getHigherRank(r));
      org.gbif.api.util.ClassificationUtils.setHigherRankKey(occ, r, match.getHigherRankKey(r));
    }
    LOG.debug("Occurrence {} matched to nub {} [{}]", occ.getKey(), occ.getScientificName(), occ.getTaxonKey());
  }

  private static String value(Map<Term, String> terms, Term term) {
    return terms.get(term);
  }
  private static boolean hasTerm(Map<Term, String> terms, Term term) {
    return !Strings.isNullOrEmpty(value(terms, term));
  }

  public void interpretTaxonomy(VerbatimOccurrence verbatim, Occurrence occ) {

    // try core taxon fields first
    OccurrenceParseResult<NameUsageMatch> matchPR = match(verbatim.getVerbatimFields());

    // try the identification extension if no core match
    if (!matchPR.isSuccessful() && verbatim.getExtensions().containsKey(Extension.IDENTIFICATION)) {
      // there may be many identifications but we only want the latest, current one
      //TODO: use latest identification only sorting records by their dwc:dateIdentified
      for (Map<Term, String> rec : verbatim.getExtensions().get(Extension.IDENTIFICATION)) {
        matchPR = match(rec);
        if (matchPR.isSuccessful()) {
          // TODO: copy other identification terms to core???
          // identifiedBy
          // dateIdentified
          // identificationReferences
          // identificationRemarks
          // identificationQualifier
          // identificationVerificationStatus
          // typeStatus
          // taxonID
          // taxonConceptID
          // nameAccordingTo
          // nameAccordingToID
          // taxonRemarks
          break;
        }
      }
    }

    // apply taxonomy if we got a match
    if (matchPR.isSuccessful()) {
      applyMatch(occ, matchPR.getPayload(), matchPR.getIssues());
    } else {
      LOG.debug("No backbone match for occurrence {}", occ.getKey());
      occ.addIssue(OccurrenceIssue.TAXON_MATCH_NONE);
    }
  }

  private Rank interpretRank(Map<Term, String> terms){
    Rank rank = null;
    if (hasTerm(terms, DwcTerm.taxonRank)) {
      rank = RANK_PARSER.parse(value(terms, DwcTerm.taxonRank)).getPayload();
    }
    // try again with verbatim if it exists
    if (rank == null && hasTerm(terms, DwcTerm.verbatimTaxonRank)) {
      rank = RANK_PARSER.parse(value(terms, DwcTerm.verbatimTaxonRank)).getPayload();
    }
    // derive from atomized fields
    if (rank == null && hasTerm(terms, DwcTerm.genus)) {
      if (hasTerm(terms, DwcTerm.specificEpithet)) {
        if (hasTerm(terms, DwcTerm.infraspecificEpithet)) {
          rank = Rank.INFRASPECIFIC_NAME;
        } else {
          rank = Rank.SPECIES;
        }
      } else {
        rank = Rank.GENUS;
      }
    }
    return rank;
  }
}
