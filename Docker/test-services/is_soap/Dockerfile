FROM niis/example-adapter:latest

# Lmit memory usage for soap mock.
ENTRYPOINT ["java", "-Xms64m", "-Xmx256m", "-jar", "/example-adapter.war"]

# Expose Tomcat
EXPOSE 8080
