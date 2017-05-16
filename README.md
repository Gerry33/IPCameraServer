# IPCameraServer
Provides a server for IP- cameras. 

- Receives pictures, videos from the camera (via FTP)

Optional:
    - converts them into any arbitrary format (using ffmpeg, to be installed separately)
    - uploads them to WEB using HTTP WEBDAV
    - Purges obsoletes local and remote files elder than x -days
    - controls motion detection dependend on presesence of devices (  
      This features is tested and working with "UPCAM"- webcams (upcam.de)
      
Ideal for running on NAS or small systems (ODROID, Raspberry, ...) 

May run as a system service. A service description file for Linux SYSTEMD is provided. 

