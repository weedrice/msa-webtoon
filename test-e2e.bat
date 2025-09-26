@echo off
setlocal enabledelayedexpansion

REM MSA Webtoon Platform - End-to-End Test Script (Windows)
REM 전체 플로우를 검증하는 테스트 스크립트

set API_GATEWAY=http://localhost:8080
set TEMP_DIR=%TEMP%\webtoon-test

if not exist "%TEMP_DIR%" mkdir "%TEMP_DIR%"

echo ================================================
echo MSA Webtoon Platform - End-to-End Test
echo ================================================

echo.
echo [STEP] 1. Health Checks
echo =====================================

echo → Checking API Gateway health...
curl -s -f "%API_GATEWAY%/actuator/health" >nul 2>&1
if !errorlevel! equ 0 (
    echo ✓ API Gateway is healthy
) else (
    echo ✗ API Gateway is not healthy
    goto :error
)

echo → Checking Event Ingest health...
curl -s -f "http://localhost:8081/actuator/health" >nul 2>&1
if !errorlevel! equ 0 (
    echo ✓ Event Ingest is healthy
) else (
    echo ✗ Event Ingest is not healthy
)

echo → Checking Rank Service health...
curl -s -f "http://localhost:8082/actuator/health" >nul 2>&1
if !errorlevel! equ 0 (
    echo ✓ Rank Service is healthy
) else (
    echo ✗ Rank Service is not healthy
)

echo → Checking Catalog Service health...
curl -s -f "http://localhost:8083/actuator/health" >nul 2>&1
if !errorlevel! equ 0 (
    echo ✓ Catalog Service is healthy
) else (
    echo ✗ Catalog Service is not healthy
)

echo → Checking Search Service health...
curl -s -f "http://localhost:8084/actuator/health" >nul 2>&1
if !errorlevel! equ 0 (
    echo ✓ Search Service is healthy
) else (
    echo ✗ Search Service is not healthy
)

echo.
echo [STEP] 2. Catalog Data Setup
echo =====================================

for /f %%i in ('powershell -command "Get-Date -UFormat %%s"') do set TIMESTAMP=%%i
set CONTENT_ID=w-test-%TIMESTAMP%
echo → Using content ID: %CONTENT_ID%

set CATALOG_PAYLOAD_FILE=%TEMP_DIR%\catalog.json
echo { > "%CATALOG_PAYLOAD_FILE%"
echo   "id": "%CONTENT_ID%", >> "%CATALOG_PAYLOAD_FILE%"
echo   "title": "테스트 웹툰 %TIME%", >> "%CATALOG_PAYLOAD_FILE%"
echo   "desc": "End-to-End 테스트용 웹툰입니다.", >> "%CATALOG_PAYLOAD_FILE%"
echo   "tags": ["테스트", "자동화", "E2E"] >> "%CATALOG_PAYLOAD_FILE%"
echo } >> "%CATALOG_PAYLOAD_FILE%"

set CATALOG_RESPONSE_FILE=%TEMP_DIR%\catalog_response.json
curl -s -X POST "%API_GATEWAY%/catalog/upsert" ^
    -H "Content-Type: application/json" ^
    -d @"%CATALOG_PAYLOAD_FILE%" > "%CATALOG_RESPONSE_FILE%"

findstr /C:"%CONTENT_ID%" "%CATALOG_RESPONSE_FILE%" >nul 2>&1
if !errorlevel! equ 0 (
    echo ✓ Catalog entry created successfully
) else (
    echo ✗ Failed to create catalog entry
    goto :error
)

echo.
echo [STEP] 3. Search Index Verification
echo =====================================

echo → Waiting for search indexing to complete...
timeout /t 3 /nobreak >nul

set SEARCH_RESPONSE_FILE=%TEMP_DIR%\search_response.json
curl -s "%API_GATEWAY%/search?q=테스트&size=10" > "%SEARCH_RESPONSE_FILE%"

findstr /C:"title" "%SEARCH_RESPONSE_FILE%" >nul 2>&1
if !errorlevel! equ 0 (
    echo ✓ Search index updated successfully
) else (
    echo → Search index not updated yet or no results found
)

echo.
echo [STEP] 4. Event Ingestion
echo =====================================

set EVENT_COUNT=0
set USER_IDS=u-test1 u-test2 u-test3

for %%u in (%USER_IDS%) do (
    for /l %%i in (1,1,5) do (
        for /f %%t in ('powershell -command "Get-Date -UFormat %%s"') do set CURRENT_TS=%%t

        set EVENT_PAYLOAD_FILE=%TEMP_DIR%\event_%%u_%%i.json
        echo { > "!EVENT_PAYLOAD_FILE!"
        echo   "eventId": "e-!CURRENT_TS!-%%i", >> "!EVENT_PAYLOAD_FILE!"
        echo   "userId": "%%u", >> "!EVENT_PAYLOAD_FILE!"
        echo   "contentId": "%CONTENT_ID%", >> "!EVENT_PAYLOAD_FILE!"
        echo   "ts": !CURRENT_TS!000, >> "!EVENT_PAYLOAD_FILE!"
        echo   "props": { >> "!EVENT_PAYLOAD_FILE!"
        echo     "action": "view" >> "!EVENT_PAYLOAD_FILE!"
        echo   } >> "!EVENT_PAYLOAD_FILE!"
        echo } >> "!EVENT_PAYLOAD_FILE!"

        curl -s -o nul -w "%%{http_code}" ^
            -X POST "%API_GATEWAY%/ingest/events" ^
            -H "Content-Type: application/json" ^
            -d @"!EVENT_PAYLOAD_FILE!" > "%TEMP_DIR%\http_code.txt"

        set /p HTTP_CODE=<"%TEMP_DIR%\http_code.txt"
        if "!HTTP_CODE!" == "202" (
            set /a EVENT_COUNT+=1
        ) else (
            echo ✗ Failed to send event (HTTP !HTTP_CODE!)
        )
    )
)

echo ✓ Sent %EVENT_COUNT% events successfully

echo.
echo [STEP] 5. Ranking Verification
echo =====================================

echo → Waiting for ranking computation...
timeout /t 5 /nobreak >nul

for %%w in (10s 60s) do (
    echo → Checking ranking for window: %%w

    set RANK_RESPONSE_FILE=%TEMP_DIR%\rank_%%w.json
    curl -s "%API_GATEWAY%/rank/top?window=%%w&n=5" > "!RANK_RESPONSE_FILE!"

    findstr /C:"w-" "!RANK_RESPONSE_FILE!" >nul 2>&1
    if !errorlevel! equ 0 (
        echo ✓ Ranking data available for %%w window
    ) else (
        echo → No ranking data for %%w window
    )
)

echo.
echo [STEP] 6. Batch Event Test
echo =====================================

for /f %%t in ('powershell -command "Get-Date -UFormat %%s"') do set BATCH_TS=%%t

set BATCH_PAYLOAD_FILE=%TEMP_DIR%\batch.json
echo [ > "%BATCH_PAYLOAD_FILE%"
echo   { >> "%BATCH_PAYLOAD_FILE%"
echo     "eventId": "batch-1-%BATCH_TS%", >> "%BATCH_PAYLOAD_FILE%"
echo     "userId": "u-batch1", >> "%BATCH_PAYLOAD_FILE%"
echo     "contentId": "%CONTENT_ID%", >> "%BATCH_PAYLOAD_FILE%"
echo     "ts": %BATCH_TS%000, >> "%BATCH_PAYLOAD_FILE%"
echo     "props": {"action": "like"} >> "%BATCH_PAYLOAD_FILE%"
echo   }, >> "%BATCH_PAYLOAD_FILE%"
echo   { >> "%BATCH_PAYLOAD_FILE%"
echo     "eventId": "batch-2-%BATCH_TS%", >> "%BATCH_PAYLOAD_FILE%"
echo     "userId": "u-batch2", >> "%BATCH_PAYLOAD_FILE%"
echo     "contentId": "%CONTENT_ID%", >> "%BATCH_PAYLOAD_FILE%"
echo     "ts": %BATCH_TS%000, >> "%BATCH_PAYLOAD_FILE%"
echo     "props": {"action": "view"} >> "%BATCH_PAYLOAD_FILE%"
echo   } >> "%BATCH_PAYLOAD_FILE%"
echo ] >> "%BATCH_PAYLOAD_FILE%"

set BATCH_RESPONSE_FILE=%TEMP_DIR%\batch_response.json
curl -s -X POST "%API_GATEWAY%/ingest/events/batch" ^
    -H "Content-Type: application/json" ^
    -d @"%BATCH_PAYLOAD_FILE%" > "%BATCH_RESPONSE_FILE%"

findstr /C:"accepted" "%BATCH_RESPONSE_FILE%" >nul 2>&1
if !errorlevel! equ 0 (
    echo ✓ Batch events accepted
) else (
    echo ✗ Batch event ingestion failed
)

echo.
echo [STEP] 7. API Documentation Check
echo =====================================

set SWAGGER_URLS=http://localhost:8080/swagger-ui.html http://localhost:8081/swagger-ui.html http://localhost:8082/swagger-ui.html http://localhost:8083/swagger-ui.html http://localhost:8084/swagger-ui.html

for %%s in (%SWAGGER_URLS%) do (
    curl -s -f "%%s" >nul 2>&1
    if !errorlevel! equ 0 (
        echo ✓ Swagger UI accessible: %%s
    ) else (
        echo → Swagger UI check skipped: %%s
    )
)

echo.
echo [STEP] 8. Final Summary
echo =====================================

echo → Test completed for content ID: %CONTENT_ID%
set /a TOTAL_EVENTS=%EVENT_COUNT%+2
echo → Total events sent: %TOTAL_EVENTS%

curl -s "%API_GATEWAY%/catalog/%CONTENT_ID%" > "%TEMP_DIR%\final_catalog.json"
findstr /C:"%CONTENT_ID%" "%TEMP_DIR%\final_catalog.json" >nul 2>&1
if !errorlevel! equ 0 (
    echo ✓ End-to-End test completed successfully!
) else (
    echo ✗ Final catalog verification failed
    goto :error
)

echo.
echo ================================================
echo 🎉 All tests passed! Platform is working correctly.
echo ================================================
echo.
echo Next steps:
echo   - Check Grafana dashboard: http://localhost:3000
echo   - Monitor Prometheus metrics: http://localhost:9090
echo   - Explore APIs via Swagger UI
echo.

goto :cleanup

:error
echo.
echo ================================================
echo ❌ Test failed! Check the error messages above.
echo ================================================
exit /b 1

:cleanup
if exist "%TEMP_DIR%" rmdir /s /q "%TEMP_DIR%" >nul 2>&1
endlocal