package mse.advDB;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import static org.neo4j.driver.Values.parameters;

// Libraries for JSON parsing
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Example {

    public static void main(String[] args) throws IOException, InterruptedException {
        String jsonPath = System.getenv("JSON_FILE");
        System.out.println("Path to JSON file is " + jsonPath);
        int nbArticles = Integer.max(1000,Integer.parseInt(System.getenv("MAX_NODES")));
        System.out.println("Number of articles to consider is " + nbArticles);
        String neo4jIP = System.getenv("NEO4J_IP");
        System.out.println("IP address of neo4j server is " + neo4jIP);

        Driver driver = GraphDatabase.driver("bolt://" + neo4jIP + ":7687", AuthTokens.basic("neo4j", "test"));
        boolean connected = false;
        do {
            try {
                System.out.println("Sleeping a bit waiting for the db");    
                Thread.yield();   
                Thread.sleep(5000); // let some time for the neo4j container to be up and running

                driver.verifyConnectivity();
                connected = true;
            }
            catch(Exception e) {
                System.err.println("Connection failed: " + e.getMessage());
            }
        } while(!connected);

        FileReader fr = new FileReader(jsonPath);
        BufferedReader br = new BufferedReader(fr);
        System.out.println("Reading first lines of the json file and parsing them:");

        // Create the Jackson ObjectMapper
        ObjectMapper mapper = new ObjectMapper();

        // Read the first 5 lines to test the parsing
        for (int i = 0; i < 5 ; i++) {
            String line = br.readLine();
            if (line == null) break; // Stop if we reach the end of the file

            // Parse the string line into a JsonNode tree
            JsonNode articleNode = mapper.readTree(line);

            // Extract the fields we need using Jackson
            String articleId = articleNode.has("id") ? articleNode.get("id").asText() : "No ID";
            String title = articleNode.has("title") ? articleNode.get("title").asText() : "No Title";

            System.out.println("\n--- Parsed Article " + (i+1) + " ---");
            System.out.println("Article ID: " + articleId);
            System.out.println("Title: " + title);

            // Extract Authors
            if (articleNode.has("authors")) {
                System.out.println("Authors:");
                for (JsonNode authorNode : articleNode.get("authors")) {
                    String authorName = authorNode.has("name") ? authorNode.get("name").asText() : "Unknown";
                    System.out.println("  - " + authorName);
                }
            }

            // Extract References (CITES)
            if (articleNode.has("references")) {
                System.out.println("References (Cites):");
                for (JsonNode refNode : articleNode.get("references")) {
                    System.out.println("  - " + refNode.asText());
                }
            }
        }

        driver.close();
        br.close();
        fr.close();
    }
}