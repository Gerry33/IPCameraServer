# IPCameraServer
Provides a server for IP- cameras. Highly configurable. Suitable for running on NAS or small systems (ODROID, Raspberry, ...) due to low ressource demand. It may run as a system service. A service description file for Linux SYSTEMD is provided. In WINDOWS creating a task in task manager fullfills the same. 

## Features
 - Receives pictures, videos from any IP- camera (via FTP)
 - converts them into any arbitrary format using ffmpeg (to be installed separately)
 - uploads them to WEB using HTTP WEBDAV
 - Purges obsoletes local and remote files elder than x -days
 - controls motion detection dependend on presence of  IP- devices e.g. mobile phone. If you're at home, no pictures/videos are taken.
     

## Motion detection control:
- If you're at home, do not take pictures and videos: Switch the camera's motion control ON or OFF depending on the presence of an IP- device in W/LAN (e.g. mobile phone). (This features is tested and working with "UPCAM"- webcams (upcam.de), but may simply be adapted to other cams by configuration.)
Cron time expressions may be provides that define where motion MUST be ON regardless of a any device presence e.g. at night when your're at home, but asleep - typically ;-) 


# Getting started
Download ipcamserver.jar, CameraServer.properties.
Edit CameraServer.properties: The parameters are all obvious and lots of comments give explanation.
Optional download log4j.xml and edit the log files location inside. Set log level in line <Logger name="com.gsi" level="debug">  from 'debug' to 'info'. 

Start the server with 'java -jar ipCamServer.jar'. 
- Watch the proper operation
- Watch the log files

If all is fine, send the program to background: 'java -jar ipCamServer.jar & '
if all is still fine, install a system service using the provided service description file. 



-- To be continued very soon --


