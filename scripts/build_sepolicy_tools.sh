#!/usr/bin/env bash
# =============================================================================
# build_sepolicy_tools.sh
# Builds secilc and sepolicy-analyze as ARM64 static binaries using Android NDK
# Sources: AOSP external/selinux (libsepol + checkpolicy)
# =============================================================================
set -euo pipefail

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
AOSP_SELINUX_TAG="${AOSP_SELINUX_TAG:-android-15.0.0_r1}"
AOSP_SELINUX_REPO="https://android.googlesource.com/platform/external/selinux"

NDK_VERSION="${NDK_VERSION:-r27c}"
NDK_DIR="${NDK_DIR:-$HOME/android-ndk}"
NDK_API="35"
ARCH="aarch64"
TARGET="${ARCH}-linux-android${NDK_API}"

OUTPUT_DIR="${OUTPUT_DIR:-$(pwd)/app/src/main/assets/bin}"
BUILD_DIR="${BUILD_DIR:-/tmp/sepolicy_build}"

# ---------------------------------------------------------------------------
# Colors for output
# ---------------------------------------------------------------------------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

# ---------------------------------------------------------------------------
# Step 1: Install NDK if not present
# ---------------------------------------------------------------------------
install_ndk() {
    if [ -f "$NDK_DIR/ndk-build" ]; then
        log_info "NDK already present at $NDK_DIR"
        return 0
    fi

    log_info "Downloading Android NDK $NDK_VERSION..."
    local NDK_ZIP="android-ndk-${NDK_VERSION}-linux.zip"
    local NDK_URL="https://dl.google.com/android/repository/${NDK_ZIP}"

    wget -q --show-progress -O "/tmp/${NDK_ZIP}" "$NDK_URL"
    log_info "Extracting NDK..."
    unzip -q "/tmp/${NDK_ZIP}" -d "$HOME/"
    mv "$HOME/android-ndk-${NDK_VERSION}" "$NDK_DIR"
    rm -f "/tmp/${NDK_ZIP}"
    log_info "NDK installed at $NDK_DIR"
}

# ---------------------------------------------------------------------------
# Step 2: Clone AOSP selinux userspace source
# ---------------------------------------------------------------------------
clone_source() {
    if [ -d "$BUILD_DIR/selinux" ]; then
        log_info "Source already cloned, skipping"
        return 0
    fi

    log_info "Cloning AOSP external/selinux at tag $AOSP_SELINUX_TAG..."
    mkdir -p "$BUILD_DIR"
    git clone \
        --depth 1 \
        --branch "$AOSP_SELINUX_TAG" \
        "$AOSP_SELINUX_REPO" \
        "$BUILD_DIR/selinux"
    log_info "Source cloned"
}

# ---------------------------------------------------------------------------
# Step 3: Set up NDK toolchain variables
# ---------------------------------------------------------------------------
setup_toolchain() {
    local TOOLCHAIN="$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64"

    export CC="${TOOLCHAIN}/bin/${TARGET}-clang"
    export AR="${TOOLCHAIN}/bin/llvm-ar"
    export RANLIB="${TOOLCHAIN}/bin/llvm-ranlib"
    export STRIP="${TOOLCHAIN}/bin/llvm-strip"

    # Static build flags
    export CFLAGS="-O2 -static -fPIE -DANDROID -D_GNU_SOURCE \
        -DDISABLE_SETRANS -DDISABLE_BOOL \
        -I${BUILD_DIR}/selinux/libsepol/include \
        -I${BUILD_DIR}/selinux/libselinux/include"
    export LDFLAGS="-static -pie"

    log_info "Toolchain: $CC"
}

# ---------------------------------------------------------------------------
# Step 4: Build libsepol (static library — dependency for both tools)
# ---------------------------------------------------------------------------
build_libsepol() {
    log_info "Building libsepol..."

    local SRC="$BUILD_DIR/selinux/libsepol"
    local OUT="$BUILD_DIR/libsepol_out"
    mkdir -p "$OUT"

    # Source files needed for a minimal static libsepol
    local SRCS=(
        src/assertion.c
        src/avrule_block.c
        src/avtab.c
        src/boolean_record.c
        src/booleans.c
        src/conditional.c
        src/constraint.c
        src/context.c
        src/context_record.c
        src/debug.c
        src/ebitmap.c
        src/expand.c
        src/handle.c
        src/hashtab.c
        src/hierarchy.c
        src/interfaces.c
        src/iface_record.c
        src/link.c
        src/mls.c
        src/module.c
        src/module_to_cil.c
        src/node_record.c
        src/nodes.c
        src/optimize.c
        src/policy.c
        src/policydb.c
        src/port_record.c
        src/ports.c
        src/roles.c
        src/role_record.c
        src/services.c
        src/sidtab.c
        src/symtab.c
        src/user_record.c
        src/users.c
        src/util.c
        src/write.c
        src/kernel_to_cil.c
        src/kernel_to_conf.c
        src/cil/cil_binary.c
        src/cil/cil_build_ast.c
        src/cil/cil.c
        src/cil/cil_copy_ast.c
        src/cil/cil_fqn.c
        src/cil/cil_lexer.l.c
        src/cil/cil_list.c
        src/cil/cil_log.c
        src/cil/cil_mem.c
        src/cil/cil_parser.c
        src/cil/cil_policy.c
        src/cil/cil_post.c
        src/cil/cil_reset_ast.c
        src/cil/cil_resolve_ast.c
        src/cil/cil_strpool.c
        src/cil/cil_symtab.c
        src/cil/cil_tree.c
        src/cil/cil_verify.c
        src/cil/cil_write_ast.c
    )

    local OBJ_FILES=()
    local failed=0

    for src in "${SRCS[@]}"; do
        local src_path="$SRC/$src"
        if [ ! -f "$src_path" ]; then
            log_warn "Source not found, skipping: $src"
            continue
        fi
        local obj="$OUT/$(echo "$src" | tr '/' '_').o"
        $CC $CFLAGS -c "$src_path" -o "$obj" 2>/dev/null || {
            log_warn "Failed to compile: $src"
            failed=$((failed + 1))
        }
        OBJ_FILES+=("$obj")
    done

    if [ $failed -gt 5 ]; then
        log_error "Too many compilation failures in libsepol ($failed files)"
        return 1
    fi

    $AR rcs "$BUILD_DIR/libsepol.a" "${OBJ_FILES[@]}"
    $RANLIB "$BUILD_DIR/libsepol.a"
    log_info "libsepol.a built successfully"
}

# ---------------------------------------------------------------------------
# Step 5: Build secilc
# ---------------------------------------------------------------------------
build_secilc() {
    log_info "Building secilc..."

    local SRC="$BUILD_DIR/selinux/secilc"
    local OUT="$BUILD_DIR/secilc_out"
    mkdir -p "$OUT"

    # secilc has a simple main + the CIL parts are in libsepol
    $CC $CFLAGS \
        -I"$BUILD_DIR/selinux/libsepol/include" \
        -I"$BUILD_DIR/selinux/libsepol/src" \
        "$SRC/secilc.c" \
        "$BUILD_DIR/libsepol.a" \
        $LDFLAGS \
        -o "$OUT/secilc" 2>&1

    if [ ! -f "$OUT/secilc" ]; then
        log_error "secilc binary not produced"
        return 1
    fi

    $STRIP "$OUT/secilc"
    local size
    size=$(du -sh "$OUT/secilc" | cut -f1)
    log_info "secilc built: $size"

    cp "$OUT/secilc" "$OUTPUT_DIR/secilc"
    chmod +x "$OUTPUT_DIR/secilc"
    log_info "secilc → $OUTPUT_DIR/secilc"
}

# ---------------------------------------------------------------------------
# Step 6: Build sepolicy-analyze
# ---------------------------------------------------------------------------
build_sepolicy_analyze() {
    log_info "Building sepolicy-analyze..."

    local SRC="$BUILD_DIR/selinux/sepolicy-analyze"
    local OUT="$BUILD_DIR/sepolicy_analyze_out"
    mkdir -p "$OUT"

    # Collect all .c files in sepolicy-analyze directory
    local C_FILES=()
    while IFS= read -r -d '' f; do
        C_FILES+=("$f")
    done < <(find "$SRC" -name "*.c" -print0 2>/dev/null)

    if [ ${#C_FILES[@]} -eq 0 ]; then
        log_warn "No .c files found in $SRC, trying alternative location..."
        # Some AOSP tags put it under checkpolicy/
        SRC="$BUILD_DIR/selinux/checkpolicy"
        while IFS= read -r -d '' f; do
            C_FILES+=("$f")
        done < <(find "$SRC" -name "sepolicy-analyze*.c" -print0 2>/dev/null)
    fi

    if [ ${#C_FILES[@]} -eq 0 ]; then
        log_error "Could not find sepolicy-analyze source files"
        return 1
    fi

    $CC $CFLAGS \
        -I"$BUILD_DIR/selinux/libsepol/include" \
        "${C_FILES[@]}" \
        "$BUILD_DIR/libsepol.a" \
        $LDFLAGS \
        -o "$OUT/sepolicy-analyze" 2>&1

    if [ ! -f "$OUT/sepolicy-analyze" ]; then
        log_error "sepolicy-analyze binary not produced"
        return 1
    fi

    $STRIP "$OUT/sepolicy-analyze"
    local size
    size=$(du -sh "$OUT/sepolicy-analyze" | cut -f1)
    log_info "sepolicy-analyze built: $size"

    cp "$OUT/sepolicy-analyze" "$OUTPUT_DIR/sepolicy-analyze"
    chmod +x "$OUTPUT_DIR/sepolicy-analyze"
    log_info "sepolicy-analyze → $OUTPUT_DIR/sepolicy-analyze"
}

# ---------------------------------------------------------------------------
# Step 7: Verify the binaries are valid ARM64 ELF
# ---------------------------------------------------------------------------
verify_binaries() {
    log_info "Verifying binaries..."
    local all_ok=true

    for binary in secilc sepolicy-analyze; do
        local path="$OUTPUT_DIR/$binary"
        if [ ! -f "$path" ]; then
            log_error "Missing: $path"
            all_ok=false
            continue
        fi

        local file_info
        file_info=$(file "$path" 2>/dev/null || echo "unknown")

        if echo "$file_info" | grep -q "aarch64\|ARM aarch64"; then
            local size
            size=$(du -sh "$path" | cut -f1)
            log_info "OK: $binary ($size) — $file_info"
        else
            log_error "Wrong architecture: $binary — $file_info"
            all_ok=false
        fi
    done

    if [ "$all_ok" = false ]; then
        log_error "Binary verification failed"
        return 1
    fi

    log_info "All binaries verified OK"
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
main() {
    log_info "Building SELinux policy tools for ARM64"
    log_info "AOSP tag:    $AOSP_SELINUX_TAG"
    log_info "NDK version: $NDK_VERSION"
    log_info "Output dir:  $OUTPUT_DIR"

    mkdir -p "$OUTPUT_DIR"

    install_ndk
    clone_source
    setup_toolchain
    build_libsepol
    build_secilc
    build_sepolicy_analyze
    verify_binaries

    log_info "================================================"
    log_info "Build complete!"
    log_info "  $OUTPUT_DIR/secilc"
    log_info "  $OUTPUT_DIR/sepolicy-analyze"
    log_info "================================================"
}

main "$@"
