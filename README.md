# Zookeeper-Clone

> A custom clone of Apache ZooKeeper — a distributed coordination service — implemented from scratch as a multi-module project.

This repository implements a **ZooKeeper-like distributed system**, including a web frontend, RPC/webserver components, and client libraries. It aims to mimic — and extend — the core capabilities of the popular Apache ZooKeeper coordination service used in distributed systems.

---

## 🚀 Project Overview

Apache ZooKeeper is a **reliable distributed coordination service** that provides configuration management, naming, and synchronization for distributed systems via a hierarchical key-value store. This project implements a similar system with modular components:

```
📦 zookeeper-clone/
├─ client/                # Client library for interacting with the Zookeeper clone
├─ frontend/              # UI/frontend for managing/znode visualization
├─ zookeeper-webserver/   # Web server serving REST/API interfaces
├─ zookeeper/             # Core server and consensus logic
|_ .github/               # Workflows and CI configuration
```

---

## 🧠 Features

✔ Structured hierarchical namespace like ZooKeeper  
✔ Watches for notifications 
✔ Ephemeral nodes 
✔ Client API for node creation, retrieval, update, deletion  
✔ Web GUI for cluster/node management  
✔ Webserver exposing REST/API endpoints  
✔ Modular design for scalability and independent development  

---

## 🏗️ Modules Explained

### 📌 `zookeeper/`
Core distributed server logic, storage backend, and API definitions. This is the “heart” of the service — maintaining znode state, watches, and client sessions.

### 📌 `zookeeper-webserver/`
HTTP/RPC server wrapping the core server with REST endpoints. Provides protocol converters and routes for clients to interact over HTTP.

### 📌 `client/`
Client SDK for interacting with the server. It exposes high-level methods like:

```js
connect();
createNode(path, data);
getNode(path);
watchNode(path, callback);
```

### 📌 `frontend/`
Frontend for browsing nodes, performing basic CRUD operations, watch values, and test ephemeral nodes.

---

## 📜 License

Apache License 2.0

---

## 🙌 Acknowledgements

Inspired by Apache ZooKeeper — a widely-used distributed coordination service for distributed systems.
