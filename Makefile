JUNIT = -cp .:./libs/junit-4.13.1.jar:./libs/hamcrest-core-1.3.jar:./libs/mockito-core-5.14.0.jar:./libs/byte-buddy-1.15.3.jar:./libs/byte-buddy-agent-1.15.3.jar
BIN_DIR = bin
TEST_DIR = tests
UNIT_TEST = tests/unit
INTEGRATION_TEST = tests/integration

# BUILD
compile: 
	javac -d $(BIN_DIR) *.java

server: 
	javac -d $(BIN_DIR) AggregationServer.java
	java -cp $(BIN_DIR) AggregationServer

content1: ContentServer.java
	javac -d $(BIN_DIR) ContentServer.java
	java -cp $(BIN_DIR) ContentServer http://localhost:4567 data/data1.txt

content2: ContentServer.java
	javac -d $(BIN_DIR) ContentServer.java
	java -cp $(BIN_DIR) ContentServer http://localhost:4567 data/data2.txt

client1: GETClient.java
	javac -d $(BIN_DIR) GETClient.java
	java -cp $(BIN_DIR) GETClient http://localhost:4567

client2: GETClient.java
	javac -d $(BIN_DIR) GETClient.java
	java -cp $(BIN_DIR) GETClient http://localhost:4567?id=aaaaaa

# TEST
test-lamport: 
	javac -d $(BIN_DIR) $(JUNIT) $(UNIT_TEST)/LamportClockTest.java
	java -cp "$(BIN_DIR):$(JUNIT)" org.junit.runner.JUnitCore LamportClockTest

test-json: 
	javac -d $(BIN_DIR) $(JUNIT) $(UNIT_TEST)/JsonParserTest.java
	java -cp "$(BIN_DIR):$(JUNIT)" org.junit.runner.JUnitCore JsonParserTest

test-weather: 
	javac -d $(BIN_DIR) $(JUNIT) $(UNIT_TEST)/WeatherNodeTest.java
	java -cp "$(BIN_DIR):$(JUNIT)" org.junit.runner.JUnitCore WeatherNodeTest

test-client: 
	javac -d $(BIN_DIR) $(JUNIT) $(UNIT_TEST)/GETClientTest.java
	java -cp "$(BIN_DIR):$(JUNIT)" org.junit.runner.JUnitCore GETClientTest

test-content: 
	javac -d $(BIN_DIR) $(JUNIT) $(UNIT_TEST)/ContentServerTest.java
	java -cp "$(BIN_DIR):$(JUNIT)" org.junit.runner.JUnitCore ContentServerTest

test-server: 
	javac -d $(BIN_DIR) $(JUNIT) $(UNIT_TEST)/AggregationServerTest.java
	java -cp "$(BIN_DIR):$(JUNIT)" org.junit.runner.JUnitCore AggregationServerTest

test-single: $(INTEGRATION_TEST)/BasicIntegrationTest.java
	javac -d $(BIN_DIR) $(JUNIT) $(INTEGRATION_TEST)/BasicIntegrationTest.java
	java -cp "$(BIN_DIR):$(JUNIT)" org.junit.runner.JUnitCore BasicIntegrationTest

test-concurrent: $(INTEGRATION_TEST)/ConcurrencyTest.java
	javac -d $(BIN_DIR) $(JUNIT) $(INTEGRATION_TEST)/ConcurrencyTest.java
	java -cp "$(BIN_DIR):$(JUNIT)" org.junit.runner.JUnitCore ConcurrencyTest

test-unit: test-lamport test-json test-client test-server test-content test-weather
test-integration: test-single test-concurrent
test: test-unit test-integration

# CLEAN
clean:
	rm -f $(BIN_DIR)/**.class
	rm -rf $(BIN_DIR)
