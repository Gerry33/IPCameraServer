# IPCameraServer
Provides a server for IP- cameras. Suitable for running on NAS or small systems (ODROID, Raspberry, Banana PI,  ...) due to low ressource demand. 

## Features
 - Receives pictures, videos from any IP- camera (via FTP)
 - converts them into any arbitrary format using ffmpeg (to be installed separately)
 - uploads them to WEB using HTTP WEBDAV
 - Purges obsoletes local and remote files elder than x -days to avoid overflows
 - controls motion detection dependend on presence of  IP- devices e.g. mobile phone. If you're at home, no pictures/videos are taken.
    
It a command line tool only that runs in the background of any system. Configuration is done by just editing one properties  text file. 

See WIKI for more details. 

-- To be continued very soon --


