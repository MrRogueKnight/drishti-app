
---

# **DRISHTI – Driving & Roving Intelligence through Smartphone Handsets Interface**

### *A Smartphone-Based Cooperative Navigation & Anti-Collision System (SIH25177 – ISRO)*

DRISHTI transforms ordinary smartphones into **intelligent, cooperative safety devices** by leveraging on-device sensors (GPS, NavIC, IMU, Camera), AI-based obstacle detection, and peer-to-peer communication to provide **real-time collision warnings** without any external hardware such as LiDAR or radar.

This project is built for **Smart India Hackathon (SIH) 2025**, based on the problem statement from **ISRO, Department of Space**:
**“Use of measurements from mobile phones to provide safe autonomous navigation on roads.”**

---

## **🔗 Project Resources**

| Type                    | Link                                                         |
| ----------------------- | ------------------------------------------------------------ |
| PowerPoint Presentation |              [Link 1](https://www.canva.com/design/DAG0PGulPQE/bUmDVseqyYGRauKZ1YViRg/edit?utm_content=DAG0PGulPQE&utm_campaign=designshare&utm_medium=link2&utm_source=sharebutton)                           |
| App UI/UX Design        |       [Link 2](https://www.canva.com/design/DAG4vkbnVr0/WWLPTw9dvx0rb-des-JK8Q/edit?utm_content=DAG4vkbnVr0&utm_campaign=designshare&utm_medium=link2&utm_source=sharebutton)                                     |
| Full Documentation      |               [Link 3](https://docs.google.com/document/d/1yZnSYFqZgoVkcXZsNtuz04FcQQTAMqyoU594vkreMy0/edit?usp=sharing)                             |
| Demo Video              | [Link 4](https://youtu.be/6OhQV3_fMPE) |

---

# **📌 Overview**

Every day, India loses 474 lives to road accidents. Two-wheelers—nearly 45% of total vehicles—lack built-in safety systems. Radar/LiDAR-based systems cost lakhs and are limited to luxury vehicles.

**DRISHTI bridges this gap** by enabling **ADAS-like safety on any smartphone** through:

* Raw GNSS + NavIC measurements
* IMU-based motion sensing
* AI-driven camera perception
* Peer-to-peer collaboration between vehicles
* Real-time collision prediction

The result: **Affordable, scalable, India-first road safety technology.**

---

# **🚀 Key Features**

### **1. Real-Time Collision Detection**

* Uses GPS/NavIC, IMU, Camera + AI, and shared peer data
* Predictive algorithms estimate relative velocity and time-to-collision
* Alerts escalate: **Safe → Warning → Danger**

### **2. Cooperative Safety Bubble**

* Vehicles share state vectors using **Wi-Fi Direct (preferred)** or Firebase/WebSockets
* No internet required for P2P
* Multi-vehicle awareness improves accuracy and safety

### **3. AI-Powered Obstacle Detection**

* YOLOv8 Nano model via TensorFlow Lite
* Detects vehicles, pedestrians, static obstacles
* Works on low-end and mid-range smartphones

### **4. Indoor Navigation Mode**

* Uses **static smartphones as base stations**
* Wi-Fi RTT + IMU for positioning
* Works in parking areas, warehouses, campuses

### **5. Hazard Awareness System**

* Alerts for blind turns, intersections, accident-prone zones
* Uses Google Maps / OpenStreetMap layers
* Supports offline hazard mapping

### **6. Post-Trip Safety Scoring (Optional)**

* Analyses braking, acceleration, overspeeding
* Helps drivers improve habits

---

# **🏗 System Architecture**

### **Modules**

| Module                  | Description                         |
| ----------------------- | ----------------------------------- |
| Raw Data Acquisition    | GNSS (GPS + NavIC), IMU, Camera     |
| Sensor Fusion Engine    | EKF fusion of GNSS + IMU + Vision   |
| Cooperative Positioning | Wi-Fi Direct / Firebase P2P sharing |
| AI Object Detection     | YOLOv8 Nano (mobile optimized)      |
| Collision Prediction    | TTC, trajectory modeling            |
| Indoor Navigation       | Wi-Fi RTT + IMU                     |
| UI/UX Layer             | Map view + alerts                   |
| Cloud Optional          | Firebase for sync, logs, fleet use  |

---

# **🔬 Algorithms**

### **1. Cooperative Positioning**

Shared GNSS raw data allows:

* **Common-view satellite differencing**
* Reduction of clock/satellite errors
* Better relative accuracy than standalone GPS

### **2. Collision Prediction**

For two vehicles:

[
t^* = -\frac{(r \cdot v)}{(v \cdot v)}
]

[
s^* = |r + vt^*|
]

Alert conditions:

* (s^* < \text{safe_distance})
* (0 < t^* < \text{time_threshold})

### **3. Indoor Localization**

* Wi-Fi Round-Trip-Time (RTT)
* IMU dead-reckoning
* Anchors = static phones with known locations

---

# **📱 Technologies Used**

### **Mobile Sensors**

* GPS + **ISRO NavIC**
* Accelerometer
* Gyroscope
* Magnetometer
* Camera (optional)
* Microphone (optional)

### **AI/ML**

* YOLOv8 Nano (TFLite)
* Frame preprocessing (OpenCV)
* On-device inference (low latency)

### **Communication**

* Wi-Fi Direct (P2P)
* Bluetooth LE (discovery)
* Firebase Realtime Database (fallback)
* WebSockets

### **Development**

* **Android Studio (Kotlin/Java)**
* Jetpack Compose / XML UI
* Google Maps SDK
* OpenStreetMap (optional)

### **Backend (Optional)**

* Firebase
* Node.js + Express
* Firestore/MongoDB

---

# **📊 MVP Demo Targets**

| Metric                    | Target           |
| ------------------------- | ---------------- |
| Outdoor Relative Accuracy | **1–3 meters**   |
| Indoor Accuracy           | **< 1 meter**    |
| Collision Alert Latency   | **< 200–500 ms** |
| AI Obstacle Detection FPS | 15–20 FPS        |
| End-to-End Delay          | < 150 ms         |
| Battery Consumption       | < 20% per hour   |

---
172	
Indian Space Research Organisation (ISRO)	
Use of measurements from the mobile phones (low cost preferred) to provide a safe autonomous navigation on the roads	
Software	
SIH25177		
Smart Vehicles	


# **🎯 Why DRISHTI Is Unique**

* **India-first platform** built for NavIC
* Fully decentralized P2P system (no server needed)
* No LiDAR, no radar — **100% smartphone-based**
* Scales to millions of vehicles
* Works in rural areas, no internet required
* Complements Digital India, Smart Mobility, Atmanirbhar Bharat

---

# **📅 Roadmap**

### **Phase 1 – MVP**

* Core sensor fusion
* Wi-Fi Direct communication
* Basic collision alerts

### **Phase 2 – Indoor Navigation**

* Static phone anchors
* Wi-Fi RTT positioning

### **Phase 3 – AI Enhancements**

* YOLOv8 Nano real-time detection
* Lane & signal detection (future)

### **Phase 4 – Cloud & Fleet**

* Firebase sync
* Fleet dashboard
* Driving behavior analytics

### **Phase 5 – Ecosystem Partnerships**

* Smart City APIs
* Insurance rewards for safe driving

---

# **🛡 Privacy & Safety**

* On-device AI (no video uploaded)
* Sensor data anonymized
* Encrypted communication (TLS/SSL)
* User consent required for all data access

---

# **📥 Installation (Developer Mode)**

```bash
git clone https://github.com/mrrogueknight/drishti.git
cd drishti
open in Android Studio
Build → Run on Android Device
```

---

# **👨‍💻 Team**

## 👥 Team: Data Morphers

| S.No | Name               | Role         | Gender | Email ID                 | Mobile No. | Stream  | Academic Year |
|:----:|--------------------|--------------|:------:|--------------------------|------------|---------|---------------|
| 1    | Prashant Ranjan    | Team Leader  | M      | 24MC3035@rgipt.ac.in     | 8829013865 | B. Tech | 2nd Year      |
| 2    | Ritik Prajapati    | Team Member  | M      | 24mc3040@rgipt.ac.in     | 7307255940 | B. Tech | 2nd Year      |
| 3    | Ayush Pratap Singh | Team Member  | M      | 23ce3010@rgipt.ac.in     | 7000267227 | B. Tech | 3rd Year      |
| 4    | Sudeeksha Tripathi | Team Member  | F      | 24mc3050@rgipt.ac.in     | 8899094625 | B. Tech | 2nd Year      |
| 5    | Prateek Pandey     | Team Member  | M      | 24mc3036@rgipt.ac.in     | 9279721870 | B. Tech | 2nd Year      |
| 6    | Karan Sharma       | Team Member  | M      | 25cd3014@rgipt.ac.in     | 9627379234 | B. Tech | 1st Year      |


---

## 🤝 Contributors

We thank the following contributors for their valuable efforts, collaboration, and contributions to this project:

* **Prashant Ranjan** – [@MrRogueKnight](https://github.com/MrRogueKnight)
* **Ritik Prajapati** – [@Ritik7307](https://github.com/Ritik7307)
* **Ayush Pratap Singh** – [@Ayush-Pratap-Singh2006](https://github.com/Ayush-Pratap-Singh2006)
* **Sudeeksha Tripathi** – [@Sudeekshatripathi123](https://github.com/Sudeekshatripathi123)
* **Prateek Pandey** – [@Prateek1976](https://github.com/Prateek1976)
* **Karan Sharma** – [@25CD3014](https://github.com/25CD3014)
---


# **📜 License**

Choose based on your preference:
MIT / Apache 2.0 / GPLv3

---

# **⭐ Support the Project**

If you like this initiative, please ⭐ the repo and share it.
Together we can build safer roads for India.

---

