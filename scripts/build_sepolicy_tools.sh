#!/usr/bin/env bash
# =============================================================================
# build_sepolicy_tools.sh
# Builds secilc and sepolicy-analyze as ARM64 static binaries
# Uses Android NDK cross-compiler, sources from AOSP external/selinux
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

SELINUX_SRC="$BUILD_DIR/selinux"
OBJ_DIR="$BUILD_DIR/obj"
LIB_DIR="$BUILD_DIR/lib"

# ---------------------------------------------------------------------------
# Colors
# ---------------------------------------------------------------------------
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
log_info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }
log_debug() { echo -e "${CYAN}[DEBUG]${NC} $*"; }

# ---------------------------------------------------------------------------
# Step 1: Install NDK
# ---------------------------------------------------------------------------
install_ndk() {
    if [ -f "$NDK_DIR/ndk-build" ]; then
        log_info "NDK already present at $NDK_DIR"
        return 0
    fi
    log_info "Downloading Android NDK $NDK_VERSION..."
    wget -q --show-progress \
        "https://dl.google.com/android/repository/android-ndk-${NDK_VERSION}-linux.zip" \
        -O /tmp/ndk.zip
    unzip -q /tmp/ndk.zip -d "$HOME/"
    mv "$HOME/android-ndk-${NDK_VERSION}" "$NDK_DIR"
    rm -f /tmp/ndk.zip
    log_info "NDK installed at $NDK_DIR"
}

# ---------------------------------------------------------------------------
# Step 2: Clone source
# ---------------------------------------------------------------------------
clone_source() {
    if [ -d "$SELINUX_SRC/.git" ]; then
        log_info "Source already cloned"
        return 0
    fi
    log_info "Cloning AOSP external/selinux @ $AOSP_SELINUX_TAG ..."
    mkdir -p "$BUILD_DIR"
    git clone \
        --depth 1 \
        --branch "$AOSP_SELINUX_TAG" \
        "$AOSP_SELINUX_REPO" \
        "$SELINUX_SRC"
    log_info "Source cloned"
}

# ---------------------------------------------------------------------------
# Step 3: Inspect actual source tree structure
# ---------------------------------------------------------------------------
inspect_source() {
    log_info "Inspecting source tree..."
    log_debug "Top-level directories:"
    ls -la "$SELINUX_SRC/" | head -30

    log_debug "libsepol directory:"
    ls -la "$SELINUX_SRC/libsepol/" 2>/dev/null || log_warn "libsepol dir not found"

    log_debug "libsepol/src:"
    ls "$SELINUX_SRC/libsepol/src/" 2>/dev/null | head -20 || log_warn "libsepol/src not found"

    log_debug "libsepol/cil:"
    ls "$SELINUX_SRC/libsepol/cil/" 2>/dev/null | head -20 || log_warn "libsepol/cil not found"

    log_debug "libsepol/cil/src:"
    ls "$SELINUX_SRC/libsepol/cil/src/" 2>/dev/null | head -20 || log_warn "libsepol/cil/src not found"

    log_debug "secilc directory:"
    ls "$SELINUX_SRC/secilc/" 2>/dev/null || log_warn "secilc dir not found"

    log_debug "sepolicy-analyze directory:"
    ls "$SELINUX_SRC/sepolicy-analyze/" 2>/dev/null || log_warn "sepolicy-analyze dir not found"
}

# ---------------------------------------------------------------------------
# Step 4: Set up toolchain
# ---------------------------------------------------------------------------
setup_toolchain() {
    local TOOLCHAIN="$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64"

    if [ ! -f "${TOOLCHAIN}/bin/${TARGET}-clang" ]; then
        log_error "Clang not found at ${TOOLCHAIN}/bin/${TARGET}-clang"
        log_error "Available clang binaries:"
        ls "${TOOLCHAIN}/bin/"*clang* 2>/dev/null || true
        exit 1
    fi

    export CC="${TOOLCHAIN}/bin/${TARGET}-clang"
    export AR="${TOOLCHAIN}/bin/llvm-ar"
    export RANLIB="${TOOLCHAIN}/bin/llvm-ranlib"
    export STRIP="${TOOLCHAIN}/bin/llvm-strip"

    log_info "CC = $CC"
}

# ---------------------------------------------------------------------------
# Step 5: Detect include paths from actual source tree
# ---------------------------------------------------------------------------
detect_includes() {
    INCLUDE_FLAGS=""

    # libsepol public headers
    if [ -d "$SELINUX_SRC/libsepol/include" ]; then
        INCLUDE_FLAGS="$INCLUDE_FLAGS -I$SELINUX_SRC/libsepol/include"
        log_info "Found libsepol/include"
    fi

    # libsepol private headers (needed by libsepol internals)
    if [ -d "$SELINUX_SRC/libsepol/src" ]; then
        INCLUDE_FLAGS="$INCLUDE_FLAGS -I$SELINUX_SRC/libsepol/src"
        log_info "Found libsepol/src (private headers)"
    fi

    # CIL headers — may be in cil/include or cil/src
    for cildir in \
        "$SELINUX_SRC/libsepol/cil/include" \
        "$SELINUX_SRC/libsepol/cil/src" \
        "$SELINUX_SRC/libsepol/cil"
    do
        if [ -d "$cildir" ]; then
            INCLUDE_FLAGS="$INCLUDE_FLAGS -I$cildir"
            log_info "Found CIL headers: $cildir"
        fi
    done

    # libselinux headers (sometimes needed)
    if [ -d "$SELINUX_SRC/libselinux/include" ]; then
        INCLUDE_FLAGS="$INCLUDE_FLAGS -I$SELINUX_SRC/libselinux/include"
    fi

    export INCLUDE_FLAGS

    # Base CFLAGS
    export BASE_CFLAGS="-O2 -fPIE -D_GNU_SOURCE \
        -DANDROID \
        -DDISABLE_SETRANS \
        -DDISABLE_BOOL \
        -Wno-error \
        -Wno-unused-parameter \
        -Wno-sign-compare \
        -Wno-missing-field-initializers \
        $INCLUDE_FLAGS"

    export LDFLAGS="-static -pie"

    log_info "Include flags: $INCLUDE_FLAGS"
}

# ---------------------------------------------------------------------------
# Step 6: Compile a single C file, return 0/1
# ---------------------------------------------------------------------------
compile_file() {
    local src="$1"
    local obj="$2"
    mkdir -p "$(dirname "$obj")"

    if $CC $BASE_CFLAGS -c "$src" -o "$obj" 2>/tmp/compile_err; then
        return 0
    else
        log_warn "Failed: $(basename "$src")"
        if [ "${VERBOSE:-0}" = "1" ]; then
            cat /tmp/compile_err
        fi
        return 1
    fi
}

# ---------------------------------------------------------------------------
# Step 7: Build libsepol — auto-discover all .c files
# ---------------------------------------------------------------------------
build_libsepol() {
    log_info "Building libsepol..."

    local LIBSEPOL_SRC="$SELINUX_SRC/libsepol"
    mkdir -p "$OBJ_DIR/libsepol" "$LIB_DIR"

    # Auto-discover all .c files under libsepol/
    # This handles any AOSP tag regardless of directory structure
    local all_c_files=()
    while IFS= read -r -d '' f; do
        all_c_files+=("$f")
    done < <(find "$LIBSEPOL_SRC" -name "*.c" -print0 | sort -z)

    log_info "Found ${#all_c_files[@]} .c files in libsepol"

    local ok=0
    local failed=0
    local obj_files=()

    for src in "${all_c_files[@]}"; do
        # Create object path mirroring source path
        local rel="${src#$LIBSEPOL_SRC/}"
        local obj="$OBJ_DIR/libsepol/$(echo "$rel" | tr '/' '_').o"

        if compile_file "$src" "$obj"; then
            obj_files+=("$obj")
            ok=$((ok + 1))
        else
            failed=$((failed + 1))
        fi
    done

    log_info "libsepol: $ok compiled, $failed failed"

    if [ ${#obj_files[@]} -eq 0 ]; then
        log_error "No object files produced for libsepol"
        log_error "Showing first compile error:"
        # Rerun first file with verbose output
        $CC $BASE_CFLAGS -c "${all_c_files[0]}" -o /dev/null 2>&1 || true
        exit 1
    fi

    # If too many failures relative to total, something is fundamentally wrong
    local total=${#all_c_files[@]}
    local fail_pct=$(( failed * 100 / total ))
    if [ $fail_pct -gt 30 ]; then
        log_error "Over 30% of libsepol files failed to compile ($failed/$total)"
        log_error "Showing a compile error for diagnosis:"
        for src in "${all_c_files[@]}"; do
            local rel="${src#$LIBSEPOL_SRC/}"
            local obj="$OBJ_DIR/libsepol/$(echo "$rel" | tr '/' '_').o"
            if [ ! -f "$obj" ]; then
                $CC $BASE_CFLAGS -c "$src" -o /dev/null 2>&1 | head -20 || true
                break
            fi
        done
        exit 1
    fi

    $AR rcs "$LIB_DIR/libsepol.a" "${obj_files[@]}"
    $RANLIB "$LIB_DIR/libsepol.a"

    local size
    size=$(du -sh "$LIB_DIR/libsepol.a" | cut -f1)
    log_info "libsepol.a: $size"
}

# ---------------------------------------------------------------------------
# Step 8: Build secilc
# ---------------------------------------------------------------------------
build_secilc() {
    log_info "Building secilc..."

    # Find secilc source — may be secilc/secilc.c or secilc/main.c
    local SECILC_DIR="$SELINUX_SRC/secilc"
    if [ ! -d "$SECILC_DIR" ]; then
        log_error "secilc directory not found at $SECILC_DIR"
        log_error "Contents of $SELINUX_SRC:"
        ls "$SELINUX_SRC/"
        exit 1
    fi

    log_debug "secilc dir contents:"
    ls "$SECILC_DIR/"

    local secilc_main=""
    for candidate in \
        "$SECILC_DIR/secilc.c" \
        "$SECILC_DIR/main.c"
    do
        if [ -f "$candidate" ]; then
            secilc_main="$candidate"
            break
        fi
    done

    if [ -z "$secilc_main" ]; then
        log_error "Could not find secilc main source file"
        exit 1
    fi

    log_info "secilc main: $secilc_main"

    # Collect any additional .c files in secilc dir
    local extra_srcs=()
    while IFS= read -r -d '' f; do
        if [ "$f" != "$secilc_main" ]; then
            extra_srcs+=("$f")
        fi
    done < <(find "$SECILC_DIR" -name "*.c" -print0)

    mkdir -p "$OBJ_DIR/secilc"

    # Compile main
    local main_obj="$OBJ_DIR/secilc/main.o"
    if ! $CC $BASE_CFLAGS -c "$secilc_main" -o "$main_obj" 2>&1; then
        log_error "Failed to compile secilc main"
        exit 1
    fi

    local all_objs=("$main_obj")

    # Compile extras
    for src in "${extra_srcs[@]}"; do
        local obj="$OBJ_DIR/secilc/$(basename "$src").o"
        if compile_file "$src" "$obj"; then
            all_objs+=("$obj")
        fi
    done

    # Link
    $CC \
        "${all_objs[@]}" \
        "$LIB_DIR/libsepol.a" \
        $LDFLAGS \
        -o "$OBJ_DIR/secilc_bin" 2>&1

    if [ ! -f "$OBJ_DIR/secilc_bin" ]; then
        log_error "secilc link failed"
        exit 1
    fi

    $STRIP "$OBJ_DIR/secilc_bin"
    cp "$OBJ_DIR/secilc_bin" "$OUTPUT_DIR/secilc"
    chmod +x "$OUTPUT_DIR/secilc"

    local size
    size=$(du -sh "$OUTPUT_DIR/secilc" | cut -f1)
    log_info "secilc built: $size → $OUTPUT_DIR/secilc"
}

# ---------------------------------------------------------------------------
# Step 9: Build sepolicy-analyze
# ---------------------------------------------------------------------------
build_sepolicy_analyze() {
    log_info "Building sepolicy-analyze..."

    # Find sepolicy-analyze source directory
    local SA_DIR=""
    for candidate in \
        "$SELINUX_SRC/sepolicy-analyze" \
        "$SELINUX_SRC/sepolicy_analyze" \
        "$SELINUX_SRC/checkpolicy/sepolicy-analyze"
    do
        if [ -d "$candidate" ]; then
            SA_DIR="$candidate"
            break
        fi
    done

    if [ -z "$SA_DIR" ]; then
        log_warn "sepolicy-analyze directory not found, searching entire tree..."
        SA_DIR=$(find "$SELINUX_SRC" -name "sepolicy-analyze*" -type d 2>/dev/null | head -1)
    fi

    if [ -z "$SA_DIR" ]; then
        log_error "Could not find sepolicy-analyze source directory"
        log_error "Tree structure:"
        find "$SELINUX_SRC" -maxdepth 2 -type d
        exit 1
    fi

    log_info "sepolicy-analyze source: $SA_DIR"
    log_debug "Contents:"
    ls "$SA_DIR/"

    # Collect all .c files
    local c_files=()
    while IFS= read -r -d '' f; do
        c_files+=("$f")
    done < <(find "$SA_DIR" -name "*.c" -print0)

    if [ ${#c_files[@]} -eq 0 ]; then
        log_error "No .c files found in $SA_DIR"
        exit 1
    fi

    log_info "Found ${#c_files[@]} source files"

    mkdir -p "$OBJ_DIR/sepolicy_analyze"

    local obj_files=()
    local failed=0

    for src in "${c_files[@]}"; do
        local obj="$OBJ_DIR/sepolicy_analyze/$(basename "$src").o"
        if compile_file "$src" "$obj"; then
            obj_files+=("$obj")
        else
            failed=$((failed + 1))
        fi
    done

    if [ ${#obj_files[@]} -eq 0 ]; then
        log_error "No object files produced for sepolicy-analyze"
        # Show full error for first file
        $CC $BASE_CFLAGS -c "${c_files[0]}" -o /dev/null 2>&1 || true
        exit 1
    fi

    # Link
    $CC \
        "${obj_files[@]}" \
        "$LIB_DIR/libsepol.a" \
        $LDFLAGS \
        -o "$OBJ_DIR/sepolicy_analyze_bin" 2>&1

    if [ ! -f "$OBJ_DIR/sepolicy_analyze_bin" ]; then
        log_error "sepolicy-analyze link failed"
        exit 1
    fi

    $STRIP "$OBJ_DIR/sepolicy_analyze_bin"
    cp "$OBJ_DIR/sepolicy_analyze_bin" "$OUTPUT_DIR/sepolicy-analyze"
    chmod +x "$OUTPUT_DIR/sepolicy-analyze"

    local size
    size=$(du -sh "$OUTPUT_DIR/sepolicy-analyze" | cut -f1)
    log_info "sepolicy-analyze built: $size → $OUTPUT_DIR/sepolicy-analyze"
}

# ---------------------------------------------------------------------------
# Step 10: Verify
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

        local info size
        info=$(file "$path" 2>/dev/null || echo "unknown")
        size=$(du -sh "$path" | cut -f1)

        if echo "$info" | grep -qE "aarch64|ARM aarch64"; then
            log_info "OK: $binary ($size)"
            log_debug "    $info"
        else
            log_error "Wrong arch or not ELF: $binary"
            log_error "    $info"
            all_ok=false
        fi
    done

    if [ "$all_ok" = false ]; then
        exit 1
    fi

    log_info "All binaries verified"
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
main() {
    log_info "============================================"
    log_info "SELinux Policy Tools — ARM64 Build"
    log_info "AOSP tag:   $AOSP_SELINUX_TAG"
    log_info "NDK:        $NDK_VERSION (API $NDK_API)"
    log_info "Output:     $OUTPUT_DIR"
    log_info "Build dir:  $BUILD_DIR"
    log_info "============================================"

    mkdir -p "$OUTPUT_DIR" "$BUILD_DIR" "$OBJ_DIR" "$LIB_DIR"

    install_ndk
    clone_source
    inspect_source
    setup_toolchain
    detect_includes
    build_libsepol
    build_secilc
    build_sepolicy_analyze
    verify_binaries

    log_info "============================================"
    log_info "Build complete!"
    ls -lh "$OUTPUT_DIR/"
    log_info "============================================"
}

main "$@"
