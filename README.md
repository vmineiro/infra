# BaseAnalysis Infrastructure

Infrastructure services that support the BaseAnalysis project ecosystem.

## Overview

This repository contains infrastructure service configurations that are reusable across multiple projects in the BaseAnalysis ecosystem. These services provide essential development and deployment infrastructure but are separate from the application code.

## Services

### Gitea
Self-hosted Git server for version control.
- **Location**: `gitea/`
- **Description**: Lightweight Git service with web UI
- **Documentation**: [gitea/README.md](gitea/README.md)
- **Status**: Available but optional (GitHub is currently primary)

### Portainer
Container management UI for Docker deployments.
- **Location**: `portainer/`
- **Description**: Web-based Docker management interface
- **Documentation**: See `docs/PORTAINER_*.md`
- **Status**: Available for container management

## Documentation

Infrastructure deployment and operations documentation:
- [PORTAINER_DEPLOYMENT_GUIDE.md](docs/PORTAINER_DEPLOYMENT_GUIDE.md) - Complete Portainer setup guide
- [PORTAINER_GIT_DEPLOY.md](docs/PORTAINER_GIT_DEPLOY.md) - Git-based automatic deployment with Portainer
- [DOCKGE_DEPLOYMENT_PLAN.md](docs/DOCKGE_DEPLOYMENT_PLAN.md) - Alternative container management platform

## Application Repository

The base-data-etl application is maintained in a separate repository:
- **Location**: `../base-data-etl/`
- **Purpose**: Public contract data ETL and analysis
- **Documentation**: [../base-data-etl/README.md](../base-data-etl/README.md)

## Usage

This infrastructure repository is separate from application code to:
1. **Enable reusability** across multiple projects
2. **Isolate infrastructure changes** from application changes
3. **Simplify CI/CD** - application doesn't need infrastructure service configs
4. **Allow independent versioning** of infrastructure and application

## Getting Started

To deploy infrastructure services:

1. **Gitea** (if needed):
   ```bash
   cd gitea/
   docker-compose up -d
   ```
   See [gitea/README.md](gitea/README.md) for configuration details.

2. **Portainer** (if needed):
   Follow the guide in [docs/PORTAINER_DEPLOYMENT_GUIDE.md](docs/PORTAINER_DEPLOYMENT_GUIDE.md)

## Maintenance

Infrastructure services are independent of application deployments:
- Update infrastructure services without affecting the application
- Scale infrastructure services separately
- Use different infrastructure for different projects

## Contributing

When adding new infrastructure services:
1. Create a dedicated folder for the service
2. Include a README.md with setup instructions
3. Document integration points with applications
4. Keep service configurations generic and reusable
