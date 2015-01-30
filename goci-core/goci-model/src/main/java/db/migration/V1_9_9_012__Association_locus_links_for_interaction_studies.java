package db.migration;

import org.flywaydb.core.api.migration.spring.SpringJdbcMigration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Javadocs go here!
 *
 * @author Tony Burdett
 * @date 30/01/15
 */
public class V1_9_9_012__Association_locus_links_for_interaction_studies extends FieldSplitter
        implements SpringJdbcMigration {
    private static final String SELECT_GENES =
            "SELECT ID, GENE FROM GWASGENE";

    private static final String SELECT_SNPS =
            "SELECT ID, SNP FROM GWASSNP";

    private static final String SELECT_ASSOCIATIONS_AND_SNPS =
            "SELECT DISTINCT ID, STRONGESTALLELE, GENE, SNP " +
                    "FROM GWASSTUDIESSNP " +
                    "WHERE SNP NOT LIKE '%,%' " +
                    "AND SNP LIKE '%:%' " +
                    "AND SNP NOT LIKE 'chr%:%' " +
                    "AND SNP NOT LIKE 'HLA-%:%' " +
                    "AND SNP NOT LIKE 'DRB%:%' " +
                    "AND SNP NOT IN ('hg18_chr11:27369242','B*38:02','A*11:01','B*55:02','ch2:211694960','A*02:01')";

    private final Logger log = LoggerFactory.getLogger(getClass());

    protected Logger getLog() {
        return log;
    }

    public void migrate(JdbcTemplate jdbcTemplate) throws Exception {
        // get all genes
        IdAndStringRowHandler geneHandler = new IdAndStringRowHandler();
        jdbcTemplate.query(SELECT_GENES, geneHandler);
        final Map<Long, String> geneIdToNameMap = geneHandler.getIdToStringMap();

        // get all snps
        IdAndStringRowHandler snpHandler = new IdAndStringRowHandler();
        jdbcTemplate.query(SELECT_SNPS, snpHandler);
        final Map<Long, String> snpIdToRsIdMap = snpHandler.getIdToStringMap();

        // get all associations and link to gene id
        final Map<Long, Set<Long>> associationIdToGeneIds = new HashMap<>();
        final Map<Long, List<Long>> associationIdToSnpIds = new HashMap<>();
        final Map<Long, List<String>> associationIdToRiskAlleleNames = new HashMap<>();
        jdbcTemplate.query(SELECT_ASSOCIATIONS_AND_SNPS, (resultSet, i) -> {
            long associationID = resultSet.getLong(1);

            List<String> riskAlleles;
            List<String> genes;
            List<String> snps;

            String riskAlleleStr = resultSet.getString(2);
            if (riskAlleleStr != null) {
                riskAlleles = split(resultSet.getString(2).trim(), "x", ":");
            }
            else {
                riskAlleles = new ArrayList<>();
            }

            String genesStr = resultSet.getString(3);
            if (genesStr != null) {
                genes = split(genesStr.trim());
            }
            else {
                genes = new ArrayList<>();
            }

            String snpsStr = resultSet.getString(4);
            if (snpsStr != null) {
                snps = split(snpsStr.trim(), ":");
            }
            else {
                snps = new ArrayList<>();
            }

            for (String gene : genes) {
                for (long geneID : geneIdToNameMap.keySet()) {
                    if (geneIdToNameMap.get(geneID).equals(gene)) {
                        if (!associationIdToGeneIds.containsKey(associationID)) {
                            associationIdToGeneIds.put(associationID, new HashSet<>());
                        }
                        if (!associationIdToGeneIds.get(associationID).contains(geneID)) {
                            // add the new associated gene
                            associationIdToGeneIds.get(associationID).add(geneID);
                        }
                        break; // we break here to handle duplicate entries in the gene table of the database
                    }
                }
            }

            Iterator<String> snpIterator = snps.iterator();
            Iterator<String> riskAlleleIterator = riskAlleles.iterator();
            if (snps.size() == riskAlleles.size()) {
                while(snpIterator.hasNext()) {
                    String snp = snpIterator.next().trim();
                    String riskAllele = riskAlleleIterator.next().trim();

                    for (long snpID : snpIdToRsIdMap.keySet()) {
                        if (snpIdToRsIdMap.get(snpID).equals(snp)) {
                            if (!associationIdToSnpIds.containsKey(associationID)) {
                                associationIdToSnpIds.put(associationID, new ArrayList<>());
                                associationIdToRiskAlleleNames.put(associationID, new ArrayList<>());
                            }
                            if (!associationIdToSnpIds.get(associationID).contains(snpID)) {
                                // add the new associated snp and risk allele
                                associationIdToSnpIds.get(associationID).add(snpID);
                                associationIdToRiskAlleleNames.get(associationID).add(riskAllele);
                            }
                            break; // we break here to handle duplicate entries in the snp table of the database
                        }
                    }
                }
            }
            else {
                getLog().warn("Mismatched number of snps and risk alleles for " +
                                      "association " + associationID + " " +
                                      "(snp string = " + snpsStr + " and " +
                                      "risk allele string = " + riskAlleleStr + ").  " +
                                      "Inferring risk alleles from SNP");
                while (snpIterator.hasNext()) {
                    String snp = snpIterator.next().trim();
                    String riskAllele = snp + "-?";

                    for (long snpID : snpIdToRsIdMap.keySet()) {
                        if (snpIdToRsIdMap.get(snpID).equals(snp)) {
                            if (!associationIdToSnpIds.containsKey(associationID)) {
                                associationIdToSnpIds.put(associationID, new ArrayList<>());
                                associationIdToRiskAlleleNames.put(associationID, new ArrayList<>());
                            }
                            if (!associationIdToSnpIds.get(associationID).contains(snpID)) {
                                // add the new associated snp and risk allele
                                associationIdToSnpIds.get(associationID).add(snpID);
                                associationIdToRiskAlleleNames.get(associationID).add(riskAllele);
                            }
                            break; // we break here to handle duplicate entries in the snp table of the database
                        }
                    }
                }
            }
            return null;
        });

        SimpleJdbcInsert insertLocus =
                new SimpleJdbcInsert(jdbcTemplate)
                        .withTableName("LOCUS")
                        .usingColumns("HAPLOTYPE_SNP_COUNT", "DESCRIPTION")
                        .usingGeneratedKeyColumns("ID");

        SimpleJdbcInsert insertAssociationLocus =
                new SimpleJdbcInsert(jdbcTemplate)
                        .withTableName("ASSOCIATION_LOCUS")
                        .usingColumns("ASSOCIATION_ID", "LOCUS_ID");

        SimpleJdbcInsert insertRiskAllele =
                new SimpleJdbcInsert(jdbcTemplate)
                        .withTableName("RISK_ALLELE")
                        .usingColumns("RISK_ALLELE_NAME")
                        .usingGeneratedKeyColumns("ID");

        SimpleJdbcInsert insertLocusRiskAllele =
                new SimpleJdbcInsert(jdbcTemplate)
                        .withTableName("LOCUS_RISK_ALLELE")
                        .usingColumns("LOCUS_ID", "RISK_ALLELE_ID");

        SimpleJdbcInsert insertRiskAlleleSnp =
                new SimpleJdbcInsert(jdbcTemplate)
                        .withTableName("RISK_ALLELE_SNP")
                        .usingColumns("RISK_ALLELE_ID", "SNP_ID");

        SimpleJdbcInsert insertAuthorReportedGene =
                new SimpleJdbcInsert(jdbcTemplate)
                        .withTableName("AUTHOR_REPORTED_GENE")
                        .usingColumns("LOCUS_ID", "REPORTED_GENE_ID");

        for (Long associationID : associationIdToSnpIds.keySet()) {
            // get snp/risk allele pairs
            List<Long> snps = associationIdToSnpIds.get(associationID);
            List<String> riskAlleles = associationIdToRiskAlleleNames.get(associationID);

            if (snps.size() != riskAlleles.size()) {
                throw new RuntimeException("Mismatched SNP ID/Risk Allele name pairs for " +
                                                   "association " + associationID + " (" + snps + ", " + riskAlleles +
                                                   ")");
            }
            else {
                // iterate over each SNP and risk allele
                Iterator<Long> snpIterator = snps.iterator();
                Iterator<String> riskAlleleIterator = riskAlleles.iterator();

                while (riskAlleleIterator.hasNext()) {
                    // create multiple LOCI, one per SNP/risk allele pair, and get the locus ID
                    Map<String, Object> locusArgs = new HashMap<>();
                    locusArgs.put("HAPLOTYPE_SNP_COUNT", null);
                    String description = snps.size() == 2 ? "SNP x SNP interaction" : "SNP x SNP x SNP interaction";
                    locusArgs.put("DESCRIPTION", description);
                    Number locusID = insertLocus.executeAndReturnKey(locusArgs);

                    // now create the ASSOCIATION_LOCUS link
                    Map<String, Object> associationLocusArgs = new HashMap<>();
                    associationLocusArgs.put("ASSOCIATION_ID", associationID);
                    associationLocusArgs.put("LOCUS_ID", locusID);
                    insertAssociationLocus.execute(associationLocusArgs);

                    Long snpID = snpIterator.next();
                    String riskAlleleName = riskAlleleIterator.next();

                    // now create a single RISK_ALLELE and get the risk allele ID
                    Map<String, Object> riskAlleleArgs = new HashMap<>();
                    riskAlleleArgs.put("RISK_ALLELE_NAME", riskAlleleName);
                    Number riskAlleleID = insertRiskAllele.executeAndReturnKey(riskAlleleArgs);

                    // now create the LOCUS_RISK_ALLELE link
                    Map<String, Object> locusRiskAlleleArgs = new HashMap<>();
                    locusRiskAlleleArgs.put("LOCUS_ID", locusID.longValue());
                    locusRiskAlleleArgs.put("RISK_ALLELE_ID", riskAlleleID.longValue());
                    insertLocusRiskAllele.execute(locusRiskAlleleArgs);

                    // now create the RISK_ALLELE_SNP link
                    try {
                        Map<String, Object> riskAlleleSnpArgs = new HashMap<>();
                        riskAlleleSnpArgs.put("RISK_ALLELE_ID", riskAlleleID.longValue());
                        riskAlleleSnpArgs.put("SNP_ID", snpID);
                        insertRiskAlleleSnp.execute(riskAlleleSnpArgs);
                    }
                    catch (DataIntegrityViolationException e) {
                        throw new RuntimeException(
                                "Failed to insert link between snp = " + snpID + " and risk allele = " + riskAlleleID,
                                e);
                    }

                    // finally create the AUTHOR_REPORTED_GENE link
                    if (associationIdToGeneIds.containsKey(associationID)) {
                        for (Long geneID : associationIdToGeneIds.get(associationID)) {
                            try {
                                Map<String, Object> authorReportedGeneArgs = new HashMap<>();
                                authorReportedGeneArgs.put("LOCUS_ID", locusID.longValue());
                                authorReportedGeneArgs.put("REPORTED_GENE_ID", geneID);
                                insertAuthorReportedGene.execute(authorReportedGeneArgs);
                            }
                            catch (DataIntegrityViolationException e) {
                                throw new RuntimeException(
                                        "Failed to insert link between locus = " + locusID + " and reported gene  = " +
                                                geneID,
                                        e);
                            }
                        }
                    }
                }
            }
        }
    }

    public class IdAndStringRowHandler implements RowCallbackHandler {
        private Map<Long, String> idToStringMap;

        public IdAndStringRowHandler() {
            this.idToStringMap = new HashMap<>();
        }

        @Override public void processRow(ResultSet resultSet) throws SQLException {
            idToStringMap.put(resultSet.getLong(1), resultSet.getString(2).trim());
        }

        public Map<Long, String> getIdToStringMap() {
            return idToStringMap;
        }
    }
}

