# AI Model Advisor - Java Multi-Agent Pipeline

4-agent system that uses **Google Custom Search API** (live web) + **Gemini 2.5 Flash** (reasoning)
to recommend the best AI model for any use case. Refactored into a **Multi-Agent microservice architecture** for Cloud Run.

```
IntakeAgent  ->  SearchAgent  ->  AnalysisAgent  ->  RecommendationAgent
    [1]             [2]               [3]                 [4]
 Parse input   Google Search      Score models        Final output
 [REST API]      [REST API]         [REST API]          [REST API]
```

## Architecture Refactor

The project has been refactored from a single monolithic application into a multi-agent system where each agent is an independent REST service.

- **A2A Protocol**: Agents communicate via typed JSON payloads over HTTP POST.
- **Microservices**: Each agent can be run as a standalone service (using Spring Boot).
- **Orchestrator**: `AdvisorPipeline.java` remains the entry point, coordinating the multi-agent chain via HTTP calls.
- **Cloud Portable**: Containerized with Docker and ready for Google Cloud Run.

## Prerequisites

- Java 17+
- Gradle 9+
- Google API key with Gemini API access
- Google Cloud project with Custom Search JSON API enabled
- A configured Google Programmable Search Engine

## Running the Multi-Agent App

### 1. Start the Agents (Local)

You can run the entire system locally using the Spring Boot application. By default, it exposes all agent endpoints.

```bash
gradle bootRun
```

This starts the server on `http://localhost:8080`.

### 2. Run the Pipeline (Entry Point)

With the agents running, use the `AdvisorPipeline` to drive the workflow.

```bash
# Interactive mode
java -cp build/libs/ai-model-advisor-1.0.0.jar com.devcamp.advisor.pipeline.AdvisorPipeline
```

## Deployment to Google Cloud Run

Each agent can be deployed as an individual Cloud Run service.

### Dockerize

```bash
docker build -t gcr.io/[PROJECT_ID]/advisor-agent .
```

### Deploying Individual Agents

Set the `AGENT_TYPE` environment variable to restrict a service to a specific agent's functionality.

1. **Intake Agent**: `gcloud run deploy intake --image ... --set-env-vars AGENT_TYPE=intake`
2. **Search Agent**: `gcloud run deploy search --image ... --set-env-vars AGENT_TYPE=search`
3. **Analysis Agent**: `gcloud run deploy analysis --image ... --set-env-vars AGENT_TYPE=analysis`
4. **Recommendation Agent**: `gcloud run deploy recommendation --image ... --set-env-vars AGENT_TYPE=recommendation`

### Configuring the Pipeline

Once deployed, set the following environment variables for the service running the `AdvisorPipeline`:

- `INTAKE_AGENT_URL`
- `SEARCH_AGENT_URL`
- `ANALYSIS_AGENT_URL`
- `RECOMMENDATION_AGENT_URL`

## Architecture Components

| File | Role |
|------|------|
| `AdvisorApplication.java` | Spring Boot main entry point for agents |
| `IntakeController.java` | REST Controller for Agent 1 |
| `SearchController.java` | REST Controller for Agent 2 |
| `AnalysisController.java` | REST Controller for Agent 3 |
| `RecommendationController.java` | REST Controller for Agent 4 |
| `AdvisorPipeline.java` | Orchestrator - coordinates HTTP calls between agents |
| `AgentRequests.java` | Multi-input request DTOs for the A2A protocol |
| `AgentModels.java` | Core data contracts |
| `Dockerfile` | Multi-agent container definition |

## API Costs
... (rest of the content)
