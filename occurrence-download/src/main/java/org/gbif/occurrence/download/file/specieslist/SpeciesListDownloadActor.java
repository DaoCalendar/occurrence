package org.gbif.occurrence.download.file.specieslist;

import static org.gbif.occurrence.download.file.OccurrenceMapReader.buildInterpretedOccurrenceMap;
import java.io.IOException;
import java.util.Date;
import java.util.Map;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.converters.DateConverter;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.gbif.occurrence.download.file.DownloadFileWork;
import org.gbif.occurrence.download.file.common.DatasetUsagesCollector;
import org.gbif.occurrence.download.file.common.SearchQueryProcessor;
import org.gbif.occurrence.download.hive.DownloadTerms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.common.base.Throwables;
import akka.actor.UntypedActor;

public class SpeciesListDownloadActor extends UntypedActor {
  private static final Logger LOG = LoggerFactory.getLogger(SpeciesListDownloadActor.class);

  static {
    // https://issues.apache.org/jira/browse/BEANUTILS-387
    ConvertUtils.register(new DateConverter(null), Date.class);
  }


  @Override
  public void onReceive(Object message) throws Exception {
    if (message instanceof DownloadFileWork) {
      doWork((DownloadFileWork) message);
    } else {
      unhandled(message);
    }
  }

  /**
   * Executes the job.query and creates a data file that will contains the records from job.from to
   * job.to positions.
   */
  private void doWork(DownloadFileWork work) throws IOException {

    DatasetUsagesCollector datasetUsagesCollector = new DatasetUsagesCollector();
    SpeciesListCollector speciesCollector = new SpeciesListCollector();
    try {
      SearchQueryProcessor.processQuery(work, occurrence -> {
        try {
          Map<String, String> occurrenceRecordMap = buildInterpretedOccurrenceMap(occurrence, DownloadTerms.SPECIES_LIST_TERMS);
          if (occurrenceRecordMap != null) {
            // collect usages
            datasetUsagesCollector.collectDatasetUsage(occurrenceRecordMap.get(GbifTerm.datasetKey.simpleName()),
                occurrenceRecordMap.get(DcTerm.license.simpleName()));
            speciesCollector.collect(occurrenceRecordMap);
          }
        } catch (Exception e) {
          getSender().tell(e, getSelf()); // inform our master
          throw Throwables.propagate(e);
        }
      });

      getSender().tell(new SpeciesListResult(work, datasetUsagesCollector.getDatasetUsages(), datasetUsagesCollector.getDatasetLicenses(),
        speciesCollector.getDistinctSpecies()), getSelf());
    } finally {
      // Release the lock
      work.getLock().unlock();
      LOG.info("Lock released, job detail: {} ", work);
    }
  }
}
