# check=skip=InvalidDefaultArgInFrom
# Gradle supplies mandatory exact version arguments from frontend metadata.
ARG NODE_VERSION
ARG PLAYWRIGHT_VERSION
FROM node:${NODE_VERSION}-bookworm-slim AS node-runtime
FROM mcr.microsoft.com/playwright:v${PLAYWRIGHT_VERSION}-noble
ARG PNPM_VERSION
# Keep the test runtime identical to the Gradle-managed host pin.
COPY --from=node-runtime /usr/local/bin/node /usr/local/bin/node
COPY --from=node-runtime /usr/local/lib/node_modules/npm /opt/npm
ENV PNPM_HOME=/opt/pnpm
ENV PATH=/opt/pnpm/bin:/usr/local/bin:$PATH
RUN node /opt/npm/bin/npm-cli.js install --global --prefix /opt/pnpm pnpm@${PNPM_VERSION}
WORKDIR /workspace
COPY . .
RUN pnpm install --frozen-lockfile && pnpm exec ng build ui
# Browser binaries and system libraries come from the version-matched Playwright base image.
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright
