@echo off
setlocal

echo ============================================================
echo Silo / MinIO connectivity check
echo ============================================================
echo S3 API should listen on 9000, console on 9001.

echo.
echo [1] S3 health  http://127.0.0.1:9000/minio/health/live
curl -s -o NUL -w "    HTTP %%{http_code}  (expect 200)" http://127.0.0.1:9000/minio/health/live
echo.

echo [2] S3 root    http://127.0.0.1:9000/
curl -s -o NUL -w "    HTTP %%{http_code}  (expect 403 AccessDenied)" http://127.0.0.1:9000/
echo.

echo [3] Console    http://127.0.0.1:9001/
curl -s -o NUL -w "    HTTP %%{http_code}  (expect 200)" http://127.0.0.1:9001/
echo.

echo [4] Credentials (SigV4 list buckets, expect 200 and XML)
curl -s --max-time 10 --aws-sigv4 "aws:amz:us-east-1:s3" --user "minioadmin:minioadmin" -o NUL -w "    HTTP %%{http_code}  (expect 200)" http://127.0.0.1:9000/
echo.
echo.

echo If any check fails: confirm silo.exe is running and ports 9000/9001 are listening.
exit /b 0
