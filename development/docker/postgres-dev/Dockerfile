ARG DOCKER_REGISTRY=""
FROM ${DOCKER_REGISTRY}postgres:18

# Copy performance configuration
COPY --chown=postgres:postgres files/postgresql-performance.conf /etc/postgresql/18/main/postgresql-performance.conf

# Append include directive to postgresql.conf.sample
# PostgreSQL copies this sample file when initializing new databases, so all new databases will include our config
RUN echo "include = '/etc/postgresql/18/main/postgresql-performance.conf'" >> /usr/share/postgresql/postgresql.conf.sample
