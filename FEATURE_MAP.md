# Feature Map

This file documents the key features of the DRISHTI application.

## Network Screen

*   **Real-time Sensor Status:** The network screen displays the real-time status of the following sensors:
    *   GPS/NavIC
    *   IMU Sensors
    *   Camera (AI Vision)
    *   Wi-Fi/Bluetooth
*   **System Configuration:** The network screen allows users to configure the following system settings:
    *   **Sensitivity Level:** Adjusts the sensitivity of the collision detection system. Options include "Low," "Medium," and "High."
    *   **Safe Distance:** Sets the minimum safe distance for forward collision warnings. The value can be adjusted from 20m to 100m.
*   **Data Persistence:** The sensitivity level and safe distance settings are persisted across application launches using `PreferenceStore`.
