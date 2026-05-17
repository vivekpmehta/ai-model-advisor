# AI Model Advisor - Java Multi-Agent Pipeline

4-agent system that uses **Google Custom Search API** (live web) + **Gemini 2.5 Flash** (reasoning)
to recommend the best AI model for any use case. No hardcoded model database.

```
IntakeAgent  ->  SearchAgent  ->  AnalysisAgent  ->  RecommendationAgent
    [1]             [2]               [3]                 [4]
 Parse input   Google Search      Score models        Final output
 + generate     fetch live          against             with primary,
   queries       results           requirements        runner-up, budget
```

## Prerequisites

- Java 17+
- Gradle
- Google API key with Gemini API access
- Google Cloud project with Custom Search JSON API enabled
- A configured Google Programmable Search Engine

## Setup

### 1. Google API Key

1. Go to [Google Cloud Console](https://console.cloud.google.com)
2. Create a project and enable "Generative Language API" and "Custom Search JSON API"
3. Create an API key under APIs & Services -> Credentials

```bash
export GOOGLE_API_KEY=AIzaSy...
```

> Note: Google Custom Search JSON API is closed to new customers as of 2026.
> Existing keys continue to work through January 2027.
> New projects should evaluate [Vertex AI Search](https://cloud.google.com/vertex-ai-search)

### 2. Google Programmable Search Engine ID

1. Go to [Programmable Search Engine](https://programmablesearchengine.google.com)
2. Create a new engine and set it to "Search the entire web"
3. Copy the Search Engine ID (cx value)

```bash
export GOOGLE_SEARCH_ENGINE_ID=a1b2c3d4e5f6...
```

## Run

```bash
# Interactive mode
./gradlew run

# With use case as argument
./gradlew run --args="RAG chatbot over internal docs, 500 queries/day, startup budget"

# Build fat jar and run
./gradlew jar
java -jar build/libs/ai-model-advisor-1.0.0.jar "Your use case here"
```

## Architecture

| File | Role |
|------|------|
| `AppConfig.java` | Centralised config from env vars |
| `DefaultModel.java` | Shared Google Gemini 2.5 Flash wrapper |
| `GoogleSearchClient.java` | HTTP client for Google Custom Search API |
| `IntakeAgent.java` | Agent 1 - parse use case to structured requirements + search queries |
| `SearchAgent.java` | Agent 2 - execute Google searches to discovered model candidates |
| `AnalysisAgent.java` | Agent 3 - score candidates against requirements |
| `RecommendationAgent.java` | Agent 4 - final synthesis to recommendation |
| `AdvisorPipeline.java` | Orchestrator - wires agents, drives A2A handoffs |
| `AgentModels.java` | Typed DTOs passed between agents |

## API Costs

| Service | Cost |
|---------|------|
| Google Custom Search | Free: 100 queries/day. Paid: $5 per 1000 queries |
| Gemini 2.5 Flash | Depends on current Google AI pricing and token usage |

One pipeline run = 4 Google queries + 4 Gemini API calls.
