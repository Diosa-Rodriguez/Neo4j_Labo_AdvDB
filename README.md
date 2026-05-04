# Neo4j_Labo_AdvDB

This repository contains the source code, Docker configuration, and Kubernetes YAML deployment files for the Advanced Databases laboratory. 

The application is designed to stream an 18GB DBLP citation network dataset directly over HTTP and load it into a Neo4j database running on a Kubernetes cluster, strictly respecting the assigned memory and CPU constraints.

## Instructions & Documentation

For all setup steps, architectural requirements, and deployment details, **please follow the PDF document.**

### Key Components:
* **Java Application:** Implements memory-efficient stream processing and batch Cypher insertions.
* **Dockerfile:** Configured for cross-platform compilation (`linux/amd64`) to run on the cluster.
* **Kubernetes YAMLs:** Contains the deployment configurations for both the Neo4j database and the Java loader job.
