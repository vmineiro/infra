#!/bin/bash
# ============================================================================
# Build Jenkins Agent Images
# ============================================================================
#
# Purpose: Build Docker images for Jenkins agents with Node.js and Python
# Usage: ./build-agents.sh [node|python|all]
#
# ============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "🔨 Building Jenkins Agent Images"
echo "=================================="
echo ""

# Parse argument
BUILD_TARGET="${1:-all}"

build_node_agent() {
    echo "📦 Building Node.js Agent..."
    docker build \
        -f "${SCRIPT_DIR}/Dockerfile.node" \
        -t jenkins-node-agent:latest \
        -t jenkins-node-agent:$(date +%Y%m%d) \
        "${SCRIPT_DIR}"

    echo "✅ Node.js agent built successfully"
    docker images jenkins-node-agent
    echo ""
}

build_python_agent() {
    echo "🐍 Building Python Agent..."
    docker build \
        -f "${SCRIPT_DIR}/Dockerfile.python" \
        -t jenkins-python-agent:latest \
        -t jenkins-python-agent:$(date +%Y%m%d) \
        "${SCRIPT_DIR}"

    echo "✅ Python agent built successfully"
    docker images jenkins-python-agent
    echo ""
}

case "$BUILD_TARGET" in
    node)
        build_node_agent
        ;;
    python)
        build_python_agent
        ;;
    all)
        build_node_agent
        build_python_agent
        ;;
    *)
        echo "❌ Invalid argument: $BUILD_TARGET"
        echo "Usage: $0 [node|python|all]"
        exit 1
        ;;
esac

echo "✅ Build complete!"
echo ""
echo "📋 Next steps:"
echo "  1. Configure Jenkins Cloud templates (see JENKINS_AGENTS.md)"
echo "  2. Test agents: Jenkins → Build Executor Status"
echo "  3. Run a test job to verify"
echo ""
