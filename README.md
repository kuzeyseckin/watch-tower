# **Watch Tower**

This project is a remote-controlled robot car system consisting of a mobile application (Android) and a Raspberry Pi, capable of real-time video, audio, and command transmission over Wi-Fi. Its purpose is to bring together different engineering fields such as mobile programming, embedded systems, and network communication to create real-world applications.

## **Features**

* **Real-Time Communication:** Three separate WebSocket channels are used for video, audio, and command data between the mobile app and the robot. This modular structure allows each data stream to operate independently and quickly.  
* **Full Control:** The user interface offers the ability to control the robot's forward/backward and right/left movements, as well as manage the camera's movement via servo motors.  
* **Two-Way Audio Transmission:** The application records audio using the phone's built-in microphone and transmits this data unidirectionally to the robot. The audio data received on the Raspberry Pi is played through an external speaker.  
* **Durable Connection:** The code on both the mobile app and Raspberry Pi sides automatically attempts to reconnect in case of connection interruptions, ensuring a seamless experience.  
* **Advanced Power Management:** Specialized voltage converters are used for components with different voltage needs, and a charging circuit is used for safe battery charging.

## **Technologies Used**

* **Mobile Application:**  
  * **Language:** Kotlin  
  * **Platform:** Android  
  * **Network Communication:** org.java-websocket  
* **Robot Side:**  
  * **Platform:** Raspberry Pi  
  * **Language:** Python  
  * **Network Communication:** websockets  
  * **Camera:** picamera2 and opencv-python  
  * **Audio:** sounddevice and numpy  
  * **Control:** RPi.GPIO (motors) and pigpio (servo motors)

## **Hardware Requirements**

To bring this project to life, you will need the following hardware components:

* Raspberry Pi 3B+  
* MicroSD Card  
* 4 x L298 Mini Motor Driver Boards  
* 4 x DC Motors  
* 4x4 car chassis  
* 2 x SG90 9G Mini Servo Motors  
* 2-Axis Servo Motor Pan Tilt (Mounting bracket for servo motors)  
* 1 x 3.7V 2S LiPo Battery (7.4V)  
* 1 x 2S LiPo Battery Charging Circuit  
* 3 x XL4015 ACAV 5A DC-DC Step-Down Converters  
* 1 x PAM8403 Stereo Amplifier Module  
* 1 x 8R 2W 88dB Speaker  
* 1 x 3.5mm Jack Cable  
* 1 x SPH0645LM4H I2S MEMS Microphone (optional, for two-way audio recording)  
* Jumper Wires

## **Setup and Operation**

For the project to work seamlessly, you must carefully follow the setup steps for both the Android application and the Raspberry Pi.

### **1\. Raspberry Pi Setup**

Here are the steps required to prepare the Raspberry Pi, the brain of the robot.

#### **1.1 Installing Required Libraries**

Open the terminal on your Raspberry Pi and install the necessary Python libraries using the following commands.

pip install websockets RPi.GPIO pigpio opencv-python sounddevice numpy picamera2

#### **1.2 Hardware Connections**

This is the most critical step of the project. Ensure all components are connected correctly.

**Power Management:**

* **LiPo Battery and Charging Circuit:** Connect the **2S LiPo Battery Charging Circuit** using an external power source (e.g., a USB adapter). Connect the output of the charging circuit directly to the corresponding connections on the LiPo battery.  
* **DC-DC Converters:** Distribute the 7.4V voltage from the battery to three separate XL4015 converters:  
  * First XL4015: Set the output to 5V and connect it to the Raspberry Pi's power input.  
  * Second XL4015: Set the output to 7.4V (battery voltage) and connect it to the power inputs of the four L298 Mini drivers.  
  * Third XL4015: Set the output to 5V and connect it to the power pins of the servo motors.

**Motor Connections:**

* Each L298 Mini driver controls one DC motor.  
* Connect the control pins of L298 Mini 1 to Raspberry Pi's GPIO 17 and GPIO 18 pins.  
* Connect the control pins of L298 Mini 2 to Raspberry Pi's GPIO 22 and GPIO 23 pins.  
* Connect the control pins of L298 Mini 3 to Raspberry Pi's GPIO 25 and GPIO 8 pins.  
* Connect the control pins of L298 Mini 4 to Raspberry Pi's GPIO 7 and GPIO 1 pins.

**Servo Motor Connections:**

* Connect the signal pins of the two SG90 servos mounted on the Pan Tilt bracket to Raspberry Pi's GPIO 5 and GPIO 6 pins.  
* Connect the power pins of the servos to the 5V output from the third XL4015 converter.

**Audio and Camera Connections:**

* **Camera Module:** Plug the camera module into the CSI port on the Raspberry Pi.  
* **Audio Output:** Connect the Raspberry Pi's 3.5mm audio jack to the input of the PAM8403 amplifier. Connect the power and ground pins of the amplifier to the 5V source, and the output pins to the 8R 2W speaker.  
* **Microphone:** Connect the SPH0645LM4H I2S MEMS microphone to the relevant GPIO and I2S pins (you may need an additional guide for this process).

#### **1.3 Assigning a Static IP Address**

The mobile application is configured to communicate with the robot via a static IP address (192.168.1.50).  
Open the network configuration file in the terminal:  
sudo nano /etc/dhcpcd.conf

Add the following lines to the end of the file:

interface wlan0  
static ip\_address=192.168.1.50/24  
static routers=192.168.1.1  
static domain\_name\_servers=192.168.1.1

Save changes and reboot: Ctrl+X, Y and Enter. Then sudo reboot.

#### **1.4 Starting the Server**

Copy the main.py file to your Raspberry Pi and run it with the following command. This command will run the server in the background and return the terminal for use.

### **2\. Android Application Setup**

Follow these steps to install the Android application on your phone:

1. **Open Android Studio:** Import the project files into Android Studio.  
2. **Install Dependencies:** Android Studio will automatically install the necessary dependencies.  
3. **Build the Application:** Connect your phone via USB and click the Run button to build and install the application on your phone.

### **3\. Usage**

Ensure the robot and the mobile application are on the same Wi-Fi network.

* Use the directional buttons on the bottom left to control the robot's movement.  
* Use the colored buttons on the bottom right to change the camera's angle.  
* Press and hold the microphone icon to transmit audio from your phone to the robot.

### **4\. Remote Access (Outside Home Network)**

If you want to use the project outside your home network, the safest way is to set up a **VPN server** on your home network and connect to it from your phone. This method allows your phone to behave as if it were on the home network, without requiring any changes to the mobile application code.

## **Future Plans**

* **Sensor Integration:** Add distance sensors (e.g., HC-SR04) to the robot to develop autonomous driving features such as obstacle avoidance.  
* **Multiple Camera Control:** Get images from different camera angles and switch between these cameras in the interface.  
* **Voice Commands:** Integrate Google Assistant or similar technologies to control the robot with voice commands.