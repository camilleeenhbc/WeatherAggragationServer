# Weather Aggregation Server  
Hoang Bao Chau Nguyen  
a1874801

## Overview
This project implements a weather aggregation system consisting of an Aggregation Server, Content Server, and GET Client. The Aggregation Server aggregates weather data from multiple Content Servers via HTTP-formatted PUT requests and responds to HTTP-formatted GET requests from the GET Client.

The system uses Lamport clocks to synchronise events and ensure consistent ordering across multiple requests and interactions.

A custom `JsonParser` is implemented for handling JSON data in this submission.

### Documentation
- Design sketch: Available under the docs folder (`DESIGN SKETCH.pdf`). This is the sketch submitted earlier for feedback.
- Updated Class Diagram: The final class diagram can be found in `updatedClassDiagram.pdf`, reflecting changes made for the final submission.
- `CHANGES.pdf`: This pdf file reviews the changes I have made after draft submission with tutors' comments.

### Dependencies
All required dependencies are provided as `.jar` files located in the `libs` folder. These external libraries support automated testing. The dependencies include:
- byte-buddy-1.15.3
- byte-buddy-agent-1.15.3
- mockito-core-5.14.0
- hamcrest-core-1.3
- junit-4.13.1


## Features

### Lamport Clock
The `LamportClock` increments every time a message or request is sent and updates its value whenever a response is received. This applies to all components sending and receiving messages (`AggregationServer`, `ContentServer`, and `GETClient`).

### Aggregation Server
The Aggregation Server performs the following tasks:
- Listens to requests from Content Servers and GET Clients, handling GET and PUT requests simultaneously.
- Manages outdated content servers, removing data from servers that have not communicated within the last 30 seconds.
- Limits the stored weather data to the 20 most recent entries, removing the oldest data when necessary.

### Content Server
The Content Server uploads weather data from local files to the Aggregation Server using HTTP PUT requests. The Content Server retries 3 times if the connection fails (lost connection, server unavailable, etc.).
- Status code `201` is received when first connecting to the server.
- Status code `200` is received if the request is successful.
- Status code `204` is received if the request is successful but with no content.
- Status code `500` is returned if the file data request is invalid or Lamport-Clock is not sent.

### GET Client
The GET Client retrieves aggregated weather data from the Aggregation Server using HTTP GET requests. The GET Client retries 3 times if the connection fails (lost connection, server unavailable, etc.).
- Retrieve all the latest data stored in the Aggregation Server when no `stationId` is specified:
  - Status code `200` is received with all data if successful.
  - Status code `200` with an empty array if there's no data.
  - Status code `500` if the JSON response is invalid, malformed, etc or Lamport-Clock is not sent.
- Retrieve the latest data for a specific `stationId/ContentServer` if it exists:
  - Status code `200` is received with the latest data for the requested stationID.
  - Status code `404` if the data is not found.
  - Status code `500` if the JSON response is invalid, malformed, etc or Lamport-Clock is not sent.


## Usage

### 1. Build and Start the Aggregation Server
To build and start the Aggregation Server, which handles all requests and manages data, run the following command:
```
make server
```

### 2. Build and Start the Content Server
To build and start the Content Server:

- To upload the `data1.txt` file from the `data` folder to the Aggregation Server, run:
  ```
  make content1
  ```

- To upload the `data2.txt` file from the `data` folder to the Aggregation Server, run:
  ```
  make content2
  ```

- **Note**: To specify the data for upload, you can modify the second parameter in the Makefile as follows:
```
java -cp $(BIN_DIR) ContentServer http://localhost:4567 <file_path>
```

### 3. Build and Start the GET Client
To build and start the GET Client:

- To retrieve all data from the Aggregation Server, run:
  ```
  make client1
  ```

- To retrieve data with the ID "aaaaaa" from the Aggregation Server, run:
  ```
  make client2
  ```

- **Note**: You can modify the stationId to retrieve data for a different ID by updating the URL in the Makefile. This follows the standard HTTP request URL parameter format:
```
http://localhost:4567?id=<stationId>
```

### 4. Test
This project includes thorough unit and integration testing using JUnit and Mockito (dependencies located in the `libs` folder). Tests include:
- Unit testing for individual components, utilising mocked sockets.
- Integration testing for integration and communication testing between all components. There are 2 integration tests provided:
	- `BasicIntegrationTest`: server process sequential GET and PUT requests.
	- `ConcurrencyTest`: test simultaneous GET and PUT requests in different scenarios.

All test files located in `tests` folder.

**Note**
- `BasicIntegrationTest` runs on port 3333, and `ConcurrencyTest` runs on port 9999. Ensure these ports are available to run integration testing.
- Refer to the `Makefile` for individual test commands.
- Since these tests use multi-threading extensively, please wait for them to finish running.

#### How to run
- To run unit tests:
```
make test-unit
```

- To run integration tests:
```
make test-integration
```

- To run all tests:
```
make test
```

### 5. Clean Up
To clean up all compiled files and remove generated binaries, run:
```
make clean
```
