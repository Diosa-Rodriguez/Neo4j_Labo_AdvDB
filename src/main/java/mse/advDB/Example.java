package mse.advDB;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                Thread.sleep(5000); 

                driver.verifyConnectivity();
                connected = true;
            }
            catch(Exception e) {
                System.err.println("Connection failed: " + e.getMessage());
            }
        } while(!connected);

        // constraints
        System.out.println("Creating database constraints/indexes...");
        try (Session session = driver.session()) {
            session.writeTransaction(tx -> {
                tx.run("CREATE CONSTRAINT IF NOT EXISTS FOR (a:ARTICLE) REQUIRE a.key_id IS UNIQUE");
                tx.run("CREATE CONSTRAINT IF NOT EXISTS FOR (auth:AUTHOR) REQUIRE auth.name IS UNIQUE");
                return null;
            });
        }
        System.out.println("Constraints created successfully.");

        BufferedReader br;
        if (jsonPath.startsWith("http://") || jsonPath.startsWith("https://")) {
            System.out.println("Streaming data directly from URL: " + jsonPath);
            java.net.URL url = new java.net.URL(jsonPath);
            br = new BufferedReader(new java.io.InputStreamReader(url.openStream()));
        } else {
            System.out.println("Reading data from local file: " + jsonPath);
            br = new BufferedReader(new java.io.FileReader(jsonPath));
        }
        
        ObjectMapper mapper = new ObjectMapper();
        
        //  Batching variables
        List<Map<String, Object>> batchList = new ArrayList<>();
        int batchSize = 5000; 
        int totalProcessed = 0;

        
        String batchCypherQuery = 
            "UNWIND $batch AS row " +
            "MERGE (a:ARTICLE {key_id: row.articleId}) " +
            "ON CREATE SET a.title = row.title " +
            "ON MATCH SET a.title = row.title " +
            "FOREACH (authorName IN row.authors | " +
            "  MERGE (auth:AUTHOR {name: authorName}) " +
            "  MERGE (auth)-[:AUTHORED]->(a) " +
            ") " +
            "FOREACH (refId IN row.references | " +
            "  MERGE (cited:ARTICLE {key_id: refId}) " +
            "  MERGE (a)-[:CITES]->(cited) " +
            ")";

        System.out.println("Reading the json file and writing to Neo4j in batches...");
        
        // Start tracking time
        long startTime = System.currentTimeMillis();
        System.out.println("--- LOADING STARTED AT: " + startTime + " ms ---");

        // Loop through the file line by line up to MAX_NODES
        for (int i = 0; i < nbArticles; i++) {
            String line = br.readLine();
            if (line == null) break; 

            JsonNode articleNode = mapper.readTree(line);

        
            Map<String, Object> articleMap = new HashMap<>();
            articleMap.put("articleId", articleNode.has("id") ? articleNode.get("id").asText() : "No ID");
            articleMap.put("title", articleNode.has("title") ? articleNode.get("title").asText() : "No Title");
            
            // Extract Authors into a Java List
            List<String> authors = new ArrayList<>();
            if (articleNode.has("authors")) {
                for (JsonNode authorNode : articleNode.get("authors")) {
                    if (authorNode.has("name")) {
                        authors.add(authorNode.get("name").asText());
                    }
                }
            }
            articleMap.put("authors", authors);

            // Extract References into a Java List
            List<String> references = new ArrayList<>();
            if (articleNode.has("references")) {
                for (JsonNode refNode : articleNode.get("references")) {
                    references.add(refNode.asText());
                }
            }
            articleMap.put("references", references);

            
            batchList.add(articleMap);
            totalProcessed++;

            
            if (batchList.size() >= batchSize) {
                final List<Map<String, Object>> currentBatch = new ArrayList<>(batchList); // Copy list for the transaction
                try (Session session = driver.session()) {
                    session.writeTransaction(tx -> {
                        tx.run(batchCypherQuery, parameters("batch", currentBatch));
                        return null;
                    });
                }
                System.out.println("Inserted batch of " + batchList.size() + " records. Total processed: " + totalProcessed);
                batchList.clear(); 
            }
        }

        if (!batchList.isEmpty()) {
            try (Session session = driver.session()) {
                session.writeTransaction(tx -> {
                    tx.run(batchCypherQuery, parameters("batch", batchList));
                    return null;
                });
            }
            System.out.println("Inserted final batch of " + batchList.size() + " records. Total processed: " + totalProcessed);
            batchList.clear();
        }

        // Stop tracking time
        long endTime = System.currentTimeMillis();
        long duration = (endTime - startTime) / 1000;
        
        System.out.println("--- LOADING ENDED AT: " + endTime + " ms ---");
        System.out.println("Total loading time: " + duration + " seconds");

        
        try (Session session = driver.session()) {
            long totalArticles = session.readTransaction(tx -> tx.run("MATCH (a:ARTICLE) RETURN count(a) AS c").single().get("c").asLong());
            long totalAuthors = session.readTransaction(tx -> tx.run("MATCH (a:AUTHOR) RETURN count(a) AS c").single().get("c").asLong());
            System.out.println("Total ARTICLES loaded: " + totalArticles);
            System.out.println("Total AUTHORS loaded: " + totalAuthors);
            System.out.println("Total nodes loaded (Articles + Authors): " + (totalArticles + totalAuthors));
        }

        driver.close();
        br.close();

    }
}