package com.sathish83.sensor;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.ce.posttask.Project;
import org.sonar.api.config.Configuration;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DatabaseQuerySensor implements Sensor {

    private static final Logger LOGGER = Loggers.get(DatabaseQuerySensor.class);

    private final Configuration configuration;

    public DatabaseQuerySensor(Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void describe(SensorDescriptor sensorDescriptor) {
        sensorDescriptor.onlyOnLanguage("java");
        //sensorDescriptor.createIssuesForRuleRepositories(JavaRulesDefinition.REPOSITORY);
    }

    @Override
    public void execute(SensorContext context) {
        Project project = (Project) context.project();
        String queryFilesPath = configuration.get("database.query.files.path").orElse("");


        File queryFilesDirectory = new File(queryFilesPath);
        if (queryFilesDirectory.isDirectory()) {
            for (File file : queryFilesDirectory.listFiles()) {
                if (file.getName().endsWith(".xml")) {
                    analyzeXmlFile(file);
                }
            }
        }
    }

    private void analyzeXmlFile(File xmlFile) {
        // Load energy cost data from external source
        String energyCostDataPath = configuration.get("energy.cost.data.path").orElse("");
        Map<String, Double> energyCostData = loadEnergyCostData(energyCostDataPath);
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(xmlFile);
            NodeList queryNodes = document.getElementsByTagName("query");
            for (int i = 0; i < queryNodes.getLength(); i++) {
                Element queryElement = (Element) queryNodes.item(i);
                String query = queryElement.getTextContent();
                optimizeQuery(query);
                analyzeQuery(query, energyCostData);
            }
        } catch (Exception e) {
            LOGGER.error("Error analyzing XML file: {}", xmlFile.getName(), e);
        }
    }
    private Map<String, Double> loadEnergyCostData(String path) {
        Map<String, Double> energyCostData = new HashMap<>();

        try (CSVParser parser = CSVFormat.DEFAULT.withHeader().parse(new FileReader(path))) {
            for (CSVRecord record : parser) {
                String databaseType = record.get("DatabaseType");
                int cpu = Integer.parseInt(record.get("CPU"));
                int ram = Integer.parseInt(record.get("RAM"));
                double costPerKWh = Double.parseDouble(record.get("EnergyCostPerKWh"));
                String key = databaseType + "," + cpu + "," + ram;
                energyCostData.put(key, costPerKWh);
            }
        } catch (IOException e) {
            LOGGER.error("Error loading energy cost data", e);
        }

        return energyCostData;
    }


    private double estimateEnergyConsumption(double cpuUsage, double ramUsage) {
        // Simplified energy consumption estimation logic
        double cpuFactor = 0.1; // Example factor for CPU usage
        double ramFactor = 0.01; // Example factor for RAM usage

        // Convert percentage to fraction
        double normalizedCpuUsage = cpuUsage / 100.0;

        // Estimate energy consumption based on factors and usage metrics
        double energyConsumption = (normalizedCpuUsage * cpuFactor) + (ramUsage * ramFactor);
        return energyConsumption;
    }

    private void analyzeQuery(String query, Map<String, Double> energyCostData) {
        // Sample CPU and RAM usage metrics (in percentage and MB)
        double cpuUsage = estimateCpuUsage(query);
        double ramUsage = estimateRamUsage(query);

        // Estimate energy consumption based on query execution metrics
        double energyConsumption = estimateEnergyConsumption(cpuUsage, ramUsage);

        // Calculate cost of energy
        String databaseType = "PostgreSQL"; // Get database type
        String key = databaseType + "," + cpuUsage + "," + ramUsage;
        double energyCostPerKWh = energyCostData.getOrDefault(key, 0.0);
        double costOfEnergy = energyConsumption * energyCostPerKWh;

        // Report the cost of energy for the query
        LOGGER.info("Query: {}", query);
        LOGGER.info("Cost of Energy: ${}", costOfEnergy);
    }

    private double estimateCpuUsage(String query) {
        // Estimate CPU usage based on the complexity of the query
        // Example: More complex queries might involve more CPU usage
        return 0.5; // Example: 50% CPU usage
    }

    private double estimateRamUsage(String query) {
        // Estimate RAM usage based on the size of data involved in the query
        // Example: Large joins might involve more RAM usage
        return 150.0; // Example: 150 MB RAM usage
    }

    private void optimizeQuery(String query) {
        String rulesFilePath = configuration.get("database.query.rules.path").orElse("");
        File rulesFile = new File(rulesFilePath);
        if (!rulesFile.exists()) {
            LOGGER.error("Rules file not found: {}", rulesFilePath);
            return;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(rulesFile);

            NodeList ruleNodes = document.getElementsByTagName("rule");
            for (int i = 0; i < ruleNodes.getLength(); i++) {
                Element ruleElement = (Element) ruleNodes.item(i);
                String name = ruleElement.getElementsByTagName("name").item(0).getTextContent();
                String matchPattern = ruleElement.getElementsByTagName("match").item(0).getTextContent();
                String suggestion = ruleElement.getElementsByTagName("suggestion").item(0).getTextContent();

                Pattern pattern = Pattern.compile(matchPattern);
                Matcher matcher = pattern.matcher(query);
                if (matcher.matches()) {
                    LOGGER.info("Rule: {}", name);
                    LOGGER.info("Original Query: {}", query);
                    LOGGER.info("Suggestion: {}", suggestion.replace("$1", matcher.group(1))
                            .replace("$2", matcher.group(2))
                            .replace("$3", matcher.group(3))
                            .replace("$4", matcher.group(4)));
                    LOGGER.info("--------------------------------------------");
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error reading rules file: rules.xml", e);
        }
    }
}

