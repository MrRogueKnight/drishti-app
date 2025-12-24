import http.server
import socketserver
import os
import json
import datetime
import socket
import threading
import time
from pathlib import Path

PORT = 5000
# Ensure we resolve to an absolute path immediately
BASE_DIR = Path("location_data").resolve()

class DataSyncHandler(http.server.SimpleHTTPRequestHandler):
    def do_POST(self):
        if self.path == '/upload':
            try:
                content_length = int(self.headers['Content-Length'])
                post_data = self.rfile.read(content_length)
                
                # 1. Parse JSON
                data = json.loads(post_data.decode('utf-8'))
                
                # 2. Get Client Identity (Device ID or IP)
                client_ip = self.client_address[0]
                # Sanitize device_id to be safe for filenames
                raw_device_id = data.get("device_id", client_ip)
                device_id = "".join(x for x in str(raw_device_id) if x.isalnum() or x in "_-")
                if not device_id:
                    device_id = "unknown_device"

                # 3. Create Directory for Client
                save_dir = BASE_DIR / device_id
                save_dir.mkdir(parents=True, exist_ok=True)
                    
                # 4. Generate Filename (Current Timestamp)
                timestamp = datetime.datetime.now().strftime("%Y%m%d_%H%M%S_%f")
                filename = f"{timestamp}.json"
                file_path = save_dir / filename
                
                # 5. Save Data
                with open(file_path, 'w') as f:
                    json.dump(data, f, indent=4)
                    
                # 5.5 VERIFY FILE EXISTS (Paranoid Check)
                if file_path.exists():
                     file_size = file_path.stat().st_size
                     status_msg = f"SUCCESS: Wrote {file_size} bytes."
                else:
                     status_msg = "CRITICAL ERROR: File write appeared to succeed but file is missing!"
                    
                # PRINT TO CONSOLE
                print(f"\n" + "="*50)
                print(f" RECEIVED DATA FROM: {device_id} ({client_ip})")
                print(f" TIME: {datetime.datetime.now().strftime('%H:%M:%S')}")
                print(f" SAVED TO: {file_path}")
                print(f" STATUS: {status_msg}")
                print(f"-"*50)
                print(f" LOC : {data.get('latitude', 0.0):<10} | {data.get('longitude', 0.0)}")
                print(f" SPD : {data.get('speed', 0)} m/s")
                intent_val = data.get('intent', 'unknown')
                print(f" INTENT : {intent_val}")
                print(f" IMU : X={data.get('imu', {}).get('acc_x', 0):.2f} Y={data.get('imu', {}).get('acc_y', 0):.2f} Z={data.get('imu', {}).get('acc_z', 0):.2f}")
                print(f"="*50 + "\n")
                
                # 6. Send Response
                self.send_response(200)
                self.send_header('Content-type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps({"status": "success", "file": filename, "path": str(file_path)}).encode('utf-8'))
                
            except Exception as e:
                print(f"Error processing request: {e}")
                import traceback
                traceback.print_exc() # Print full stack trace
                self.send_response(500)
                self.end_headers()
        else:
            self.send_error(404)

def get_local_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except:
        return "127.0.0.1"

def broadcast_server_ip(ip, port):
    """Broadcasts valid JSON to port 5001"""
    broadcast_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    broadcast_socket.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)

    # Broadcast address logic
    broadcast_addr = '<broadcast>'
    
    message = json.dumps({"server_ip": ip, "server_port": port}).encode('utf-8')
    print(f"[*] Broadcasting Discovery Beacon on Port 5001...")
    
    while True:
        try:
            broadcast_socket.sendto(message, (broadcast_addr, 5001))
            time.sleep(3)
        except Exception as e:
            # print(f"Broadcast error: {e}") # Reduce noise
            time.sleep(5)

# --- IN-MEMORY ACTIVE USER STORAGE ---
# Dictionary to store latest data for each device
# Format: { "device_id": { "data": {...}, "last_seen": timestamp } }
ACTIVE_USERS = {}
CLEANUP_INTERVAL = 1 # Check every second for instant removal
TIMEOUT_SECONDS = 5 # Remove if no update for 5 minutes

def cleanup_inactive_users():
    """Removes users who haven't sent data in a while"""
    while True:
        try:
            now = time.time()
            to_remove = []
            for device_id, info in ACTIVE_USERS.items():
                if now - info["last_seen"] > TIMEOUT_SECONDS:
                    to_remove.append(device_id)
            
            for device_id in to_remove:
                del ACTIVE_USERS[device_id]
                print(f"[-] Removed inactive user: {device_id}")
                
            time.sleep(CLEANUP_INTERVAL)
        except Exception as e:
            print(f"Cleanup error: {e}")
            time.sleep(60)
            
# --- COLLISION DETECTION ENGINE ---
import math

class KinematicsEngine:
    @staticmethod
    def latlon_to_enu(lat, lon, ref_lat, ref_lon):
        """Converts Lat/Lon to Local East-North-Up (in meters) relative to a reference point."""
        R_EARTH = 6378137.0
        d_lat = math.radians(lat - ref_lat)
        d_lon = math.radians(lon - ref_lon)
        lat_rad = math.radians(ref_lat)
        
        x = R_EARTH * d_lon * math.cos(lat_rad) # East
        y = R_EARTH * d_lat # North
        return x, y

    @staticmethod
    def rotate_vector(x, y, angle_degrees):
        """Rotates a vector by a given angle (bearing). Angle 0 = North (Y-axis)."""
        # Standard rotation: x' = x cos θ - y sin θ
        # Navigation bearing: 0 is North (Y), 90 is East (X).
        # Standard math angle 0 is East (X).
        # Transform: Bearing 0 -> Math 90. Bearing 90 -> Math 0.
        # Math Angle = 90 - Bearing
        theta = math.radians(90 - angle_degrees)
        x_new = x * math.cos(theta) - y * math.sin(theta)
        y_new = x * math.sin(theta) + y * math.cos(theta)
        return x_new, y_new

    @staticmethod
    def check_collision(user_a, user_b):
        """
        Predicts collision between User A and User B using 2nd Order Kinematics.
        Returns: Risk Level ("NONE", "WARNING", "CRITICAL")
        """
        # 1. State Extraction
        lat_a, lon_a = user_a.get('latitude', 0), user_a.get('longitude', 0)
        lat_b, lon_b = user_b.get('latitude', 0), user_b.get('longitude', 0)
        
        # Spatial Filter (Optimization)
        # 1 deg lat ~ 111km. 0.001 deg ~ 111m.
        if abs(lat_a - lat_b) > 0.002 or abs(lon_a - lon_b) > 0.002:
            return "NONE" # Too far (> ~200m)

        # 2. Convert directly to relative metric coordinates (A is origin)
        p_rel_x, p_rel_y = KinematicsEngine.latlon_to_enu(lat_b, lon_b, lat_a, lon_a) # P_B - P_A
        
        # 3. Relative Velocity
        # V = Speed * [sin(bearing), cos(bearing)] (North is Y, East is X for map math, but let's stick to standard ENU: X=East, Y=North)
        # Bearing 0 (North) -> X=0, Y=1. Bearing 90 (East) -> X=1, Y=0.
        # Vx = Speed * sin(bearing)
        # Vy = Speed * cos(bearing)
        
        v_a = user_a.get('speed', 0)
        b_a = math.radians(user_a.get('bearing', 0))
        vx_a, vy_a = v_a * math.sin(b_a), v_a * math.cos(b_a)
        
        v_b = user_b.get('speed', 0)
        b_b = math.radians(user_b.get('bearing', 0))
        vx_b, vy_b = v_b * math.sin(b_b), v_b * math.cos(b_b)
        
        v_rel_x = vx_b - vx_a
        v_rel_y = vy_b - vy_a
        
        # 4. Relative Acceleration (Global Frame)
        # Need to rotate IMU (Body Frame) to Global Frame using component logic
        # Assuming phone is aligned with vehicle for simplicity or ignoring rotation for now due to complexity without quaternion
        # Simplification: Use raw acceleration if speed is high, else ignore?
        # Better: Assume dominant acceleration is forward/backward?
        # Let's try to use the raw values but rotated by bearing.
        
        imu_a = user_a.get('imu', {})
        ax_a_body = imu_a.get('acc_x', 0)
        ay_a_body = imu_a.get('acc_y', 0)
        # Rotating body frame to ENU. Phone axes definition varies.
        # Assuming Y is forward (North-ish), X is right (East-ish).
        ax_a, ay_a = KinematicsEngine.rotate_vector(ax_a_body, ay_a_body, user_a.get('bearing', 0))

        imu_b = user_b.get('imu', {})
        ax_b_body = imu_b.get('acc_x', 0)
        ay_b_body = imu_b.get('acc_y', 0)
        ax_b, ay_b = KinematicsEngine.rotate_vector(ax_b_body, ay_b_body, user_b.get('bearing', 0))

        a_rel_x = ax_b - ax_a
        a_rel_y = ay_b - ay_a
        
        # 5. TCPA Calculation (Fast 1st Order)
        # t = -(P.V) / ||V||^2
        dot_pv = p_rel_x * v_rel_x + p_rel_y * v_rel_y
        v_sq = v_rel_x**2 + v_rel_y**2
        
        # Proximity Check with Heavy IMU / GPS Fusion (< 1.0 meter)
        dist_sq = p_rel_x**2 + p_rel_y**2
        
        # High Accuracy Short Range Logic
        if dist_sq < 1.0: # Close range (< 1m)
             # "Heavy IMU" - Predict position 0.5s into future using full 2nd order Kinematics
             # This weights Acceleration (IMU) significantly for short term prediction
             pred_t = 0.5 
             fx = p_rel_x + v_rel_x*pred_t + 0.5*a_rel_x*(pred_t**2)
             fy = p_rel_y + v_rel_y*pred_t + 0.5*a_rel_y*(pred_t**2)
             pred_dist_sq = fx**2 + fy**2
            
             # If ALREADY extremely close (< 0.3m), assume collision (GPS noise tolerance)
             if dist_sq < 0.09: return "CRITICAL"

             # If predicted to stay close or get closer (< 0.5m), trigger Alert
             if pred_dist_sq < 0.25 or pred_dist_sq < dist_sq: 
                 return "CRITICAL"
             
             # Otherwise, if moving away, ignore (safe)
             return "NONE"
        
        if v_sq < 0.1: # Relative velocity too low to predict collision
            return "NONE"
            
        tcpa = -dot_pv / v_sq
        
        if 0 < tcpa < 5.0: # Collision within 5 seconds
            # Calculate distance at TCPA (1st Order)
            future_x = p_rel_x + v_rel_x * tcpa
            future_y = p_rel_y + v_rel_y * tcpa
            min_dist = math.sqrt(future_x**2 + future_y**2)
            
            # Simple Acceleration refinement (2nd Order correction)
            # P(t) = P + Vt + 0.5At^2
            future_x_2 = future_x + 0.5 * a_rel_x * tcpa**2
            future_y_2 = future_y + 0.5 * a_rel_y * tcpa**2
            min_dist_2 = math.sqrt(future_x_2**2 + future_y_2**2)
            
            # Use the more pessimistic distance (safety first)
            final_dist = min(min_dist, min_dist_2)
            
            closing_speed = math.sqrt(v_sq)

            if final_dist < 2.0 and closing_speed > 1.0:
                print(f"[ALGO] COLLISION PREDICTED! Time: {tcpa:.2f}s, Dist: {final_dist:.2f}m")
                return "CRITICAL"
                
        # --- HEAD / TAIL SQUARE LOGIC (Zone Based) ---
        # Transform B into A's Body Frame
        # A is at (0,0) facing North (Y-axis) in local ENU
        # But A's actual heading is `b_a`.
        # So Body Frame X (Right) = Global X rotated by -b_a
        # Body Frame Y (Forward) = Global Y rotated by -b_a
        
        # P_rel is B relative to A in Global ENU.
        # Rotate P_rel by -b_a to get coordinates in A's Body Frame.
        
        # Rotation logic: 
        # x' = x cos(-b) - y sin(-b) = x cos(b) + y sin(b)
        # y' = x sin(-b) + y cos(-b) = -x sin(b) + y cos(b)
        # Note: Math angle is (90 - Bearing).
        # Let's use the helper rotate_vector with negative bearing?
        # Helper rotates (x,y) by angle. "Rotates a vector by a given angle".
        # If we rotate the coordinate system by `b_a`, the point effectively rotates by `-b_a`.
        
        # Let's manually do it to be sure.
        # Bearing 0 (N). P_rel = (0, 2) (2m North). Body = (0, 2). Matches.
        # Bearing 90 (E). P_rel = (2, 0) (2m East). A is facing East. B is 2m In Front. Body = (0, 2).
        # Math angle for 90 is 0.
        
        # Let's leverage the existing helper but invert the angle to go from Global to Body?
        # Actually, `rotate_vector` rotates the VECTOR.
        # To project P_rel (Global) into Body, we rotate P_rel by -Bearing.
        
        bx_body, by_body = KinematicsEngine.rotate_vector(p_rel_x, p_rel_y, -user_a.get('bearing', 0))
        
        # --- OVERTAKING SUPPRESSION ---
        # If moving in same direction (bearing diff < 30 deg) AND safe lateral distance (> 1.5m)
        bearing_a = user_a.get('bearing', 0)
        bearing_b = user_b.get('bearing', 0)
        bearing_diff = abs(bearing_a - bearing_b)
        if bearing_diff > 180:
            bearing_diff = 360 - bearing_diff
            
        if bearing_diff < 30: # Same direction
            if abs(bx_body) > 1.5: # Safe side clearance (1.5m)
                # Suppress alert even if technically in "Tail/Head Zone" by length
                return "NONE"

        # DEFINITION OF SQUARES (in meters)
        # HEAD SQUARE: X in [-1, 1], Y in [0, 2]
        # TAIL SQUARE: X in [-1, 1], Y in [-2, 0]
        
        in_width = abs(bx_body) < 1.0
        
        if in_width:
            if 0 < by_body < 2.0:
                return "CRITICAL" # Head Collision
            if -2.0 < by_body < 0:
                return "CRITICAL" # Tail Collision (Warning?) -> User said just collision warning.
                
        return "NONE"

# Start cleanup thread
threading.Thread(target=cleanup_inactive_users, daemon=True).start()

FILE_RETENTION_SECONDS = 900 # 15 minutes
TIMEOUT_SECONDS = 5 # INSTANT REMOVAL (5 seconds)

def cleanup_old_files():
    """Deletes JSON files older than 15 minutes"""
    while True:
        try:
            now = time.time()
            if BASE_DIR.exists():
                for device_dir in BASE_DIR.iterdir():
                    if device_dir.is_dir():
                        for json_file in device_dir.glob("*.json"):
                            try:
                                # Check modification time
                                mtime = json_file.stat().st_mtime
                                if now - mtime > FILE_RETENTION_SECONDS:
                                    json_file.unlink()
                                    print(f"[Cleanup] Deleted old file: {json_file.name}")
                            except Exception as ex:
                                print(f"[Cleanup] Error deleting {json_file.name}: {ex}")
            
            time.sleep(60) # Check every minute
        except Exception as e:
            print(f"File cleanup error: {e}")
            time.sleep(60)

# Start file cleanup thread
threading.Thread(target=cleanup_old_files, daemon=True).start()

class DataSyncHandler(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):
        """Handle GET requests - mainly for fetching all active users"""
        if self.path == '/locations':
            self.send_response(200)
            self.send_header('Content-type', 'application/json')
            self.send_header('Access-Control-Allow-Origin', '*') # Allow all for dev
            self.end_headers()
            
            # Construct list of active users
            # active_users_list = []
            # for device_id, info in ACTIVE_USERS.items():
            #     user_data = info['data']
            #     user_data['id'] = device_id # Ensure ID is present
            #     active_users_list.append(user_data)
            
            # Better: list comprehension
            # response_data = [ {**info['data'], 'id': dev_id} for dev_id, info in ACTIVE_USERS.items() ]
            
            # --- CALCULATE COLLISIONS ---
            # O(N^2) but N is small (< 50 usually)
            current_ids = list(ACTIVE_USERS.keys())
            alerts = {} # map id -> alert_level

            for i in range(len(current_ids)):
                id_a = current_ids[i]
                user_a = ACTIVE_USERS[id_a]['data']
                
                for j in range(i + 1, len(current_ids)):
                    id_b = current_ids[j]
                    user_b = ACTIVE_USERS[id_b]['data']
                    
                    risk = KinematicsEngine.check_collision(user_a, user_b)
                    
                    if risk == "CRITICAL":
                        alerts[id_a] = "CRITICAL"
                        alerts[id_b] = "CRITICAL"
            
            response_data = []
            for dev_id, info in ACTIVE_USERS.items():
                user_obj = info['data'].copy()
                user_obj['id'] = dev_id
                user_obj['alertLevel'] = alerts.get(dev_id, "NONE") # Inject Alert Level
                response_data.append(user_obj)
            
            self.wfile.write(json.dumps(response_data).encode('utf-8'))
        else:
             # Default behavior (file serving if needed, or 404)
            self.send_error(404)

    def do_POST(self):
        if self.path == '/upload':
            try:
                content_length = int(self.headers['Content-Length'])
                post_data = self.rfile.read(content_length)
                
                # 1. Parse JSON
                data = json.loads(post_data.decode('utf-8'))
                
                # 2. Get Client Identity (Device ID or IP)
                client_ip = self.client_address[0]
                # Sanitize device_id to be safe for filenames
                raw_device_id = data.get("device_id", client_ip)
                device_id = "".join(x for x in str(raw_device_id) if x.isalnum() or x in "_-")
                if not device_id:
                    device_id = "unknown_device"

                # --- UPDATE ACTIVE USERS ---
                ACTIVE_USERS[device_id] = {
                    "data": data,
                    "last_seen": time.time()
                }

                # 3. Create Directory for Client
                save_dir = BASE_DIR / device_id
                save_dir.mkdir(parents=True, exist_ok=True)
                    
                # 4. Generate Filename (Current Timestamp)
                timestamp = datetime.datetime.now().strftime("%Y%m%d_%H%M%S_%f")
                filename = f"{timestamp}.json"
                file_path = save_dir / filename
                
                # 5. Save Data
                with open(file_path, 'w') as f:
                    json.dump(data, f, indent=4)
                    
                # 5.5 VERIFY FILE EXISTS (Paranoid Check)
                if file_path.exists():
                     file_size = file_path.stat().st_size
                     status_msg = f"SUCCESS: Wrote {file_size} bytes."
                else:
                     status_msg = "CRITICAL ERROR: File write appeared to succeed but file is missing!"
                    
                # PRINT TO CONSOLE
                print(f"\n" + "="*50)
                print(f" RECEIVED DATA FROM: {device_id} ({client_ip})")
                print(f" TIME: {datetime.datetime.now().strftime('%H:%M:%S')}")
                print(f" SAVED TO: {file_path}")
                print(f" ACTIVE PEERS: {len(ACTIVE_USERS)}")
                print(f"-"*50)
                print(f" LOC : {data.get('latitude', 0.0):<10} | {data.get('longitude', 0.0)}")
                print(f" SPD : {data.get('speed', 0)} m/s")
                intent_val = data.get('intent', 'unknown')
                print(f" INTENT : {intent_val}")
                print(f" IMU : X={data.get('imu', {}).get('acc_x', 0):.2f} Y={data.get('imu', {}).get('acc_y', 0):.2f} Z={data.get('imu', {}).get('acc_z', 0):.2f}")
                print(f"="*50 + "\n")
                
                # 6. Send Response
                self.send_response(200)
                self.send_header('Content-type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps({"status": "success", "file": filename, "path": str(file_path)}).encode('utf-8'))
                
            except Exception as e:
                print(f"Error processing request: {e}")
                import traceback
                traceback.print_exc() # Print full stack trace
                self.send_response(500)
                self.end_headers()
        else:
            self.send_error(404)

if __name__ == "__main__":
    # Ensure base directory exists
    BASE_DIR.mkdir(parents=True, exist_ok=True)
        
    # Try to find the most likely LAN IP
    local_ip = get_local_ip()
    
    print(f"==================================================")
    print(f" DRISHTI Centralized Data Server")
    print(f"--------------------------------------------------")
    print(f" Auto-Detected IP: {local_ip}")
    print(f" Port: {PORT}")
    print(f"")
    print(f" IF CONNECTING FROM EMULATOR: Use 10.0.2.2")
    print(f" IF CONNECTING FROM DEVICE:   Use {local_ip}")
    print(f"")
    print(f" ALL AVAILABLE IPS:")
    try:
        host_name = socket.gethostname() 
        for ip in socket.gethostbyname_ex(host_name)[2]: 
            print(f" - {ip}")
    except:
        pass
    print(f"--------------------------------------------------")
    print(f" ABSOLUTE DATA PATH: {BASE_DIR}")
    print(f" Data will be organized by Device ID in this folder.")
    print(f"==================================================")
    
    # Start Beacon
    t = threading.Thread(target=broadcast_server_ip, args=(local_ip, PORT), daemon=True)
    t.start()
    
    # Allow address reuse to prevent "Address already in use" errors on restart
    socketserver.TCPServer.allow_reuse_address = True
    
    with socketserver.TCPServer(("", PORT), DataSyncHandler) as httpd:
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            print("\nServer stopped.")


