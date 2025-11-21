# Zookeeper Client

This project contains a simplified Zookeeper Client Interface library using the RaftClient Class provided from the Apache-Ratis project.

## Interface Details
## 1. Write
```java
public String write(String key ,String value)
```
This method takes a key and a value to be stored in the state machine and returns
```java
"OK ENTRY ADDED"
```
## 2. Read
```java
public String read(String key)
```
This method takes a key and returns the value of the key if it exists in the state machine or
```java
"__NOT_FOUND__"
```
if the key doesn't exist in the state machine.
## 3. Delete
```java
public boolean delete(String key)
```
This method takes a key and returns 
```java
true
```
if the key was successfully deleted
or
```java
false
```
if the key doesn't exist in the state machine.
## Notes
In case of any error occurance the client will return 
```java
"ERROR"
``` 
for all queries
or
```java
false
```
for delete query.