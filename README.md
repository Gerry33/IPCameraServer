# IPCameraServer
Provides a serverJAVA for IP- cameras. Suitable for running on NAS or small systems (ODROID, Raspberry, Banana PI,  ...) due to low ressource demand. 

## Features
 - Receives pictures, videos from any IP- camera (via FTP)
 - converts them into any arbitrary format using ffmpeg (to be installed separately)
 - uploads them to WEB using HTTP WEBDAV
 - Purges obsoletes local and remote files elder than x -days to avoid overflows
 - controls motion detection dependend on presence of  IP- devices e.g. mobile phone. If you're at home, no pictures/videos are taken.
    
It's a command line tool only that runs in the background of any system. Configuration is done by just editing one Properties-  text file. 

## Prerequisites
- a machine running 24/7 
- JAVA SE 1.8
- a little command line experience

See WIKI for more details.
