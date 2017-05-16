# IPCameraServer
Provides a server for IP- cameras. Highly configurable

 - Receives pictures, videos from any IP- camera (via FTP)
 - converts them into any arbitrary format using ffmpeg (to be installed separately)
 - uploads them to WEB using HTTP WEBDAV
 - Purges obsoletes local and remote files elder than x -days
 - controls motion detection dependend on presence of  IP- devices e.g. mobile phone. If you're at home, no pictures/videos are taken.
     (This features is tested and working with "UPCAM"- webcams (upcam.de), but may simply be adapted to other cams by configuration.)
 
Suitable for running on NAS or small systems (ODROID, Raspberry, ...) due to low ressource demand.

May run as a system service. A service description file for Linux SYSTEMD is provided. In WINDOWS create a task manager. 

# Getting started
Download ipcamserver.jar, CameraServer.properties.
Edit CameraServer.properties: The parameters are all obvious and lots of comments give explanation.
Start the server with 'java -jar ipCamServer.jar' 

-- To be continued --
