package id.semantics;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;

import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.topbraid.shacl.rules.RuleUtil;
import org.topbraid.shacl.validation.ValidationUtil;
import org.topbraid.shacl.vocabulary.SH;

import id.semantics.helper.SwissKnife;

/**
 * A simple app to help the generation of SWeMLS-KG
 */
public class SwemlsToolkit {

    public static void main(String[] args) throws FileNotFoundException {

        String ontologyFile = "https://w3id.org/semsys/ns/swemls/ontology.ttl";
        
        String inputFolder = "input/";
        // ... File "input/swemls-patterns.ttl" is an aggregation of all patterns in the "input/patterns" folder
        String inputPatterns = inputFolder + "swemls-patterns.ttl";
        // ... File "input/swemls-shapes.ttl" is an aggregation of all SHACL constraints & rules in the "input/shapes" folder
        String inputShapes = inputFolder + "swemls-shapes.ttl";
        // ... File "swemls-sms-metadata.ttl" is the RDF Graph representation of SWeMLS metadata extracted from the SMS process
        String inputInstances = inputFolder + "swemls-sms-metadata.ttl";

        String outputFolder = "output/";
        String outputEnhancements = outputFolder + "enrichments-results.ttl";
        String outputSwemlsKG = outputFolder + "SWeMLS-KG.ttl";
        String outputValidationReport = outputFolder + "validation-reports.ttl";

        // load SHACL constraints for patterns
        Model shapesGraph = SwissKnife.initAndLoadModelFromResource(inputShapes, Lang.TURTLE);

        // load data & ontology
        Model ontoGraph = SwissKnife.initAndLoadModelFromURL(ontologyFile, Lang.TURTLE);
        Model dataGraph = SwissKnife.initAndLoadModelFromResource(inputInstances, Lang.TURTLE);

        // initialise inference graphs for rule generation and validation
        Model inferenceGraph = ModelFactory.createOntologyModel(OntModelSpec.OWL_DL_MEM_RDFS_INF);
        inferenceGraph.add(ontoGraph);
        inferenceGraph.add(dataGraph);
        inferenceGraph.setNsPrefixes(ontoGraph.getNsPrefixMap());

        // executing SHACL-AF rules for generating workflow and write the results to an output file
        Model ruleResults = RuleUtil.executeRules(inferenceGraph, shapesGraph, inferenceGraph, null);
        RDFDataMgr.write(new FileOutputStream(outputEnhancements), ruleResults, Lang.TURTLE);

        // execute SHACL constraints on dataGraphs
        Resource validationResult = ValidationUtil.validateModel(inferenceGraph, shapesGraph, false);

        // write validation report and set SHACL namespace
        Model shaclValidationReport = validationResult.getModel();
        shaclValidationReport.setNsPrefix(SH.PREFIX, SH.NS);

        // check if the validation successful
        if (shaclValidationReport.containsLiteral(null, SH.conforms, false)) {
            System.out.println("Validation Failed! See validation results at " + outputValidationReport);

        // generate final knowledge graphs
        } else {
            // load the representation of SWeMLS patterns to be added to the final KG
            Model patternGraph = SwissKnife.initAndLoadModelFromResource(inputPatterns, Lang.TURTLE);

            // create an integrated KG
            Model finalKG = ModelFactory.createDefaultModel();
            finalKG.add(dataGraph);
            finalKG.add(ruleResults);
            finalKG.add(patternGraph);

            // save to file
            RDFDataMgr.write(new FileOutputStream(outputSwemlsKG), finalKG, Lang.TURTLE);

            System.out.println("Validation Successful! See completed KG at " + outputSwemlsKG);
        }

        RDFDataMgr.write(new FileOutputStream(outputValidationReport), shaclValidationReport, Lang.TURTLE);
    }
}