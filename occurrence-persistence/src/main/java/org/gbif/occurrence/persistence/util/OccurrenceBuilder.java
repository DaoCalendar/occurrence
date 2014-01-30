package org.gbif.occurrence.persistence.util;

import org.gbif.api.model.common.Identifier;
import org.gbif.api.model.occurrence.Occurrence;
import org.gbif.api.model.occurrence.VerbatimOccurrence;
import org.gbif.api.vocabulary.Country;
import org.gbif.api.vocabulary.EndpointType;
import org.gbif.api.vocabulary.IdentifierType;
import org.gbif.api.vocabulary.OccurrenceIssue;
import org.gbif.api.vocabulary.OccurrenceSchemaType;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.TermFactory;
import org.gbif.hbase.util.ResultReader;
import org.gbif.occurrence.common.constants.FieldName;
import org.gbif.occurrence.common.converter.BasisOfRecordConverter;
import org.gbif.occurrence.persistence.OccurrenceResultReader;
import org.gbif.occurrence.persistence.api.Fragment;
import org.gbif.occurrence.persistence.constants.HBaseTableConstants;
import org.gbif.occurrence.persistence.hbase.HBaseFieldUtil;

import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import javax.validation.ValidationException;

import com.google.common.collect.Lists;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A utility class to build object models from the HBase occurrence "row".
 */
public class OccurrenceBuilder {

  private static final BasisOfRecordConverter BOR_CONVERTER = new BasisOfRecordConverter();
  private static final Logger LOG = LoggerFactory.getLogger(OccurrenceBuilder.class);

  // should never be instantiated
  private OccurrenceBuilder() {
  }

  /**
   * Builds a Fragment object from the given result, assigning the passed in key.
   *
   * @param result an HBase scan/get Result
   * @return the Fragment or null if the passed in Result is null
   * @throws ValidationException if the fragment as stored in the table is invalid
   */
  public static Fragment buildFragment(@Nullable Result result) {
    if (result == null) {
      return null;
    }

    int key = Bytes.toInt(result.getRow());

    String rawDatasetKey = OccurrenceResultReader.getString(result, FieldName.DATASET_KEY);
    if (rawDatasetKey == null) {
      throw new ValidationException("Fragment with key [" + key + "] has no datasetKey.");
    }
    UUID datasetKey = UUID.fromString(rawDatasetKey);

    Integer crawlId = OccurrenceResultReader.getInteger(result, FieldName.CRAWL_ID);
    if (crawlId == null) {
      throw new ValidationException("Fragment with key [" + key + "] has no crawlId.");
    }
    Long harvested = OccurrenceResultReader.getLong(result, FieldName.HARVESTED_DATE);
    if (harvested == null) {
      throw new ValidationException("Fragment with key [" + key + "] has no harvestedDate.");
    }
    Date harvestedDate = new Date(harvested);
    String unitQualifier = OccurrenceResultReader.getString(result, FieldName.UNIT_QUALIFIER);
    byte[] data = OccurrenceResultReader.getBytes(result, FieldName.FRAGMENT);
    byte[] dataHash = OccurrenceResultReader.getBytes(result, FieldName.FRAGMENT_HASH);
    Long created = OccurrenceResultReader.getLong(result, FieldName.CREATED);
    String rawSchema = OccurrenceResultReader.getString(result, FieldName.XML_SCHEMA);
    OccurrenceSchemaType schema;
    if (rawSchema == null) {
      // this is typically called just before updating the fragment, meaning schemaType will then be correctly set
      LOG.debug("Fragment with key [{}] has no schema type - assuming DWCA.", key);
      schema = OccurrenceSchemaType.DWCA;
    } else {
      schema = OccurrenceSchemaType.valueOf(rawSchema);
    }
    String rawProtocol = OccurrenceResultReader.getString(result, FieldName.PROTOCOL);
    EndpointType protocol = rawProtocol == null ? null : EndpointType.valueOf(rawProtocol);

    Fragment frag;
    if (schema == null || schema == OccurrenceSchemaType.DWCA) {
      frag =
        new Fragment(datasetKey, data, dataHash, Fragment.FragmentType.JSON, protocol, harvestedDate, crawlId, schema,
          null, created);
    } else {
      frag =
        new Fragment(datasetKey, data, dataHash, Fragment.FragmentType.XML, protocol, harvestedDate, crawlId, schema,
          unitQualifier, created);
    }
    frag.setKey(key);

    return frag;
  }

  /**
   * Utility to build an API Occurrence from an HBase row.
   *
   * @return A complete occurrence, or null
   */
  public static Occurrence buildOccurrence(@Nullable Result row) {
    if (row == null || row.isEmpty()) {
      return null;
    } else {
      Occurrence occ = new Occurrence();
      Integer key = Bytes.toInt(row.getRow());
      occ.setKey(key);
      occ.setAltitude(OccurrenceResultReader.getInteger(row, FieldName.I_ALTITUDE));
      occ.setBasisOfRecord(BOR_CONVERTER.toEnum(OccurrenceResultReader.getInteger(row, FieldName.I_BASIS_OF_RECORD)));
      occ.setField(DwcTerm.catalogNumber, OccurrenceResultReader.getString(row, FieldName.CATALOG_NUMBER));
      occ.setClassKey(OccurrenceResultReader.getInteger(row, FieldName.I_CLASS_ID));
      occ.setClazz(OccurrenceResultReader.getString(row, FieldName.I_CLASS));
      occ.setField(DwcTerm.collectionCode, OccurrenceResultReader.getString(row, FieldName.COLLECTION_CODE));
      occ.setDatasetKey(OccurrenceResultReader.getUuid(row, FieldName.DATASET_KEY));
      occ.setDepth(OccurrenceResultReader.getInteger(row, FieldName.I_DEPTH));
      occ.setField(DwcTerm.occurrenceID, OccurrenceResultReader.getString(row, FieldName.DWC_OCCURRENCE_ID));
      occ.setFamily(OccurrenceResultReader.getString(row, FieldName.I_FAMILY));
      occ.setFamilyKey(OccurrenceResultReader.getInteger(row, FieldName.I_FAMILY_ID));
      occ.setGenus(OccurrenceResultReader.getString(row, FieldName.I_GENUS));
      occ.setGenusKey(OccurrenceResultReader.getInteger(row, FieldName.I_GENUS_ID));
      occ
        .setPublishingCountry(Country.fromIsoCode(OccurrenceResultReader.getString(row, FieldName.PUBLISHING_COUNTRY)));
      occ.setField(DwcTerm.institutionCode, OccurrenceResultReader.getString(row, FieldName.INSTITUTION_CODE));
      occ.setCountry(Country.fromIsoCode(OccurrenceResultReader.getString(row, FieldName.I_ISO_COUNTRY_CODE)));
      occ.setKingdom(OccurrenceResultReader.getString(row, FieldName.I_KINGDOM));
      occ.setKingdomKey(OccurrenceResultReader.getInteger(row, FieldName.I_KINGDOM_ID));
      occ.setLatitude(OccurrenceResultReader.getDouble(row, FieldName.I_LATITUDE));
      occ.setLongitude(OccurrenceResultReader.getDouble(row, FieldName.I_LONGITUDE));
      occ.setModified(OccurrenceResultReader.getDate(row, FieldName.I_MODIFIED));
      occ.setMonth(OccurrenceResultReader.getInteger(row, FieldName.I_MONTH));
      occ.setTaxonKey(OccurrenceResultReader.getInteger(row, FieldName.I_NUB_ID));
      occ.setEventDate(OccurrenceResultReader.getDate(row, FieldName.I_OCCURRENCE_DATE));
      occ.setOrder(OccurrenceResultReader.getString(row, FieldName.I_ORDER));
      occ.setOrderKey(OccurrenceResultReader.getInteger(row, FieldName.I_ORDER_ID));
      occ.setPublishingOrgKey(OccurrenceResultReader.getUuid(row, FieldName.OWNING_ORG_KEY));
      occ.setPhylum(OccurrenceResultReader.getString(row, FieldName.I_PHYLUM));
      occ.setPhylumKey(OccurrenceResultReader.getInteger(row, FieldName.I_PHYLUM_ID));
      String rawEndpointType = OccurrenceResultReader.getString(row, FieldName.PROTOCOL);
      if (rawEndpointType == null) {
        LOG.warn("EndpointType is null for occurrence [{}] - possibly corrupt record.", key);
      } else {
        EndpointType endpointType = EndpointType.valueOf(rawEndpointType);
        occ.setProtocol(endpointType);
      }
      occ.setScientificName(OccurrenceResultReader.getString(row, FieldName.I_SCIENTIFIC_NAME));
      occ.setSpecies(OccurrenceResultReader.getString(row, FieldName.I_SPECIES));
      occ.setSpeciesKey(OccurrenceResultReader.getInteger(row, FieldName.I_SPECIES_ID));
      occ.setYear(OccurrenceResultReader.getInteger(row, FieldName.I_YEAR));
      occ.setField(DwcTerm.locality, OccurrenceResultReader.getString(row, FieldName.LOCALITY));
      occ.setField(DwcTerm.county, OccurrenceResultReader.getString(row, FieldName.COUNTY));
      occ.setStateProvince(OccurrenceResultReader.getString(row, FieldName.STATE_PROVINCE));
      // TODO: interpret continent into a new column
      // occ.setContinent(OccurrenceResultReader.getString(row, FieldName.CONTINENT_OCEAN)); // no enums in hbase
      occ.setField(DwcTerm.recordedBy, OccurrenceResultReader.getString(row, FieldName.COLLECTOR_NAME));
      occ.setField(DwcTerm.identifiedBy, OccurrenceResultReader.getString(row, FieldName.IDENTIFIER_NAME));
      occ.setDateIdentified(OccurrenceResultReader.getDate(row, FieldName.IDENTIFICATION_DATE));
      occ.setIdentifiers(extractIdentifiers(key, row, HBaseTableConstants.OCCURRENCE_COLUMN_FAMILY));
      occ.setIssues(extractIssues(row));

      return occ;
    }
  }

  /**
   * Utility to build an API Occurrence from an HBase row.
   *
   * @return A complete occurrence, or null
   */
  public static VerbatimOccurrence buildVerbatimOccurrence(@Nullable Result row) {
    if (row == null || row.isEmpty()) {
      return null;
    } else {
      VerbatimOccurrence verb = new VerbatimOccurrence();
      verb.setKey(Bytes.toInt(row.getRow()));
      verb.setDatasetKey(OccurrenceResultReader.getUuid(row, FieldName.DATASET_KEY));
      verb.setPublishingOrgKey(OccurrenceResultReader.getUuid(row, FieldName.OWNING_ORG_KEY));
      verb
        .setPublishingCountry(Country.fromIsoCode(OccurrenceResultReader.getString(row, FieldName.PUBLISHING_COUNTRY)));
      verb.setLastCrawled(OccurrenceResultReader.getDate(row, FieldName.HARVESTED_DATE));
      verb.setProtocol(EndpointType.fromString(OccurrenceResultReader.getString(row, FieldName.PROTOCOL)));

      // all Term fields in row are prefixed
      for (KeyValue kv : row.raw()) {
        String colName = Bytes.toString(kv.getQualifier());
        if (colName.startsWith(HBaseTableConstants.TERM_PREFIX)) {
          Term term = TermFactory.instance().findTerm(colName.substring(HBaseTableConstants.TERM_PREFIX.length()));
          verb.setField(term, Bytes.toString(kv.getValue()));
        }
      }

      return verb;
    }
  }

  private static List<Identifier> extractIdentifiers(Integer key, Result result, String columnFamily) {
    List<Identifier> records = Lists.newArrayList();
    Integer maxCount = OccurrenceResultReader.getInteger(result, FieldName.IDENTIFIER_COUNT);
    if (maxCount != null) {
      for (int count = 0; count < maxCount; count++) {
        String idCol = HBaseTableConstants.IDENTIFIER_COLUMN + count;
        String idTypeCol = HBaseTableConstants.IDENTIFIER_TYPE_COLUMN + count;
        String id = ResultReader.getString(result, columnFamily, idCol, null);
        String rawType = ResultReader.getString(result, columnFamily, idTypeCol, null);
        if (id != null && rawType != null) {
          IdentifierType idType = null;
          try {
            idType = IdentifierType.valueOf(rawType);
          } catch (IllegalArgumentException e) {
            LOG.warn("Unrecognized value for IdentifierType from field [{}] - data is corrupt.", rawType);
          }
          if (idType != null) {
            Identifier record = new Identifier();
            record.setEntityKey(key);
            record.setIdentifier(id);
            record.setType(idType);
            records.add(record);
          }
        }
      }
    }
    return records;
  }

  private static Set<OccurrenceIssue> extractIssues(Result result) {
    Set<OccurrenceIssue> issues = EnumSet.noneOf(OccurrenceIssue.class);
    for (OccurrenceIssue issue : OccurrenceIssue.values()) {
      HBaseFieldUtil.HBaseColumn column = HBaseFieldUtil.getHBaseColumn(issue);
      byte[] val = result.getValue(Bytes.toBytes(column.getColumnFamilyName()), Bytes.toBytes(column.getColumnName()));
      if (val != null) {
        issues.add(issue);
      }
    }

    return issues;
  }
}
