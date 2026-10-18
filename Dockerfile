FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src
COPY api/StockFacil.Api/StockFacil.Api.csproj api/StockFacil.Api/
RUN dotnet restore api/StockFacil.Api/StockFacil.Api.csproj
COPY api/StockFacil.Api/ api/StockFacil.Api/
RUN dotnet publish api/StockFacil.Api/StockFacil.Api.csproj -c Release -o /app/publish --no-restore

FROM mcr.microsoft.com/dotnet/aspnet:10.0
WORKDIR /app
COPY --from=build /app/publish .
ENV ASPNETCORE_URLS=http://0.0.0.0:10000
ENV ASPNETCORE_ENVIRONMENT=Production
EXPOSE 10000
USER $APP_UID
ENTRYPOINT ["dotnet", "StockFacil.Api.dll"]
