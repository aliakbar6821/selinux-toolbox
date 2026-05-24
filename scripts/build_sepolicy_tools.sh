#!/usr/bin/env bash
# =============================================================================
# build_sepolicy_tools.sh
# Builds secilc and sepolicy-analyze as ARM64 static binaries
# Sources:
#   secilc          → android.googlesource.com/platform/external/selinux
#   sepolicy-analyze → android.googlesource.com/platform/system/sepolicy
# =============================================================================
set -euo pipefail

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
AOSP_SELINUX_TAG="${AOSP_SELINUX_TAG:-android-15.0.0_r1}"
AOSP_SELINUX_REPO="https://android.googlesource.com/platform/external/selinux"
AOSP_SEPOLICY_REPO="https://android.googlesource.com/platform/system/sepolicy"

NDK_VERSION="${NDK_VERSION:-r27c}"
NDK_DIR="${NDK_DIR:-$HOME/android-ndk}"
NDK_API="35"
ARCH="aarch64"
TARGET="${ARCH}-linux-android${NDK_API}"

OUTPUT_DIR="${OUTPUT_DIR:-$(pwd)/app/src/main/assets/bin}"
BUILD_DIR="${BUILD_DIR:-/tmp/sepolicy_build}"

SELINUX_SRC="$BUILD_DIR/selinux"
SEPOLICY_SRC="$BUILD_DIR/system_sepolicy"
OBJ_DIR="$BUILD_DIR/obj"
LIB_DIR="$BUILD_DIR/lib"
GEN_DIR="$BUILD_DIR/generated"

# ---------------------------------------------------------------------------
# Colors
# ---------------------------------------------------------------------------
RED='\033[0;31m'; GREEN='\033[0;32m'
YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
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
# Step 2: Clone sources
# ---------------------------------------------------------------------------
clone_sources() {
    mkdir -p "$BUILD_DIR"

    if [ ! -d "$SELINUX_SRC/.git" ]; then
        log_info "Cloning external/selinux @ $AOSP_SELINUX_TAG ..."
        git clone \
            --depth 1 \
            --branch "$AOSP_SELINUX_TAG" \
            "$AOSP_SELINUX_REPO" \
            "$SELINUX_SRC"
        log_info "external/selinux cloned"
    else
        log_info "external/selinux already cloned"
    fi

    if [ ! -d "$SEPOLICY_SRC/.git" ]; then
        log_info "Cloning system/sepolicy @ $AOSP_SELINUX_TAG (sparse: tools/)..."
        git clone \
            --depth 1 \
            --branch "$AOSP_SELINUX_TAG" \
            --filter=blob:none \
            --sparse \
            "$AOSP_SEPOLICY_REPO" \
            "$SEPOLICY_SRC"
        cd "$SEPOLICY_SRC"
        git sparse-checkout set tools
        cd - > /dev/null
        log_info "system/sepolicy cloned"
    else
        log_info "system/sepolicy already cloned"
    fi
}

# ---------------------------------------------------------------------------
# Step 3: Inspect source structure
# ---------------------------------------------------------------------------
inspect_source() {
    log_info "=== Source tree inspection ==="

    log_debug "libsepol/src files:"
    find "$SELINUX_SRC/libsepol/src" -name "*.c" \
        -not -path "*/test*" -not -path "*/fuzz*" \
        -exec basename {} \; 2>/dev/null | tr '\n' ' '; echo

    log_debug "libsepol/cil/src files:"
    find "$SELINUX_SRC/libsepol/cil/src" -maxdepth 1 \
        -exec basename {} \; 2>/dev/null | tr '\n' ' '; echo

    log_debug "secilc:"
    ls "$SELINUX_SRC/secilc/" 2>/dev/null | tr '\n' ' '; echo

    log_debug "system/sepolicy/tools:"
    ls "$SEPOLICY_SRC/tools/" 2>/dev/null | tr '\n' ' '; echo

    log_info "=== End inspection ==="
}

# ---------------------------------------------------------------------------
# Step 4: Set up NDK toolchain
# ---------------------------------------------------------------------------
setup_toolchain() {
    local TOOLCHAIN="$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64"

    if [ ! -f "${TOOLCHAIN}/bin/${TARGET}-clang" ]; then
        log_error "Clang not found: ${TOOLCHAIN}/bin/${TARGET}-clang"
        exit 1
    fi

    export CC="${TOOLCHAIN}/bin/${TARGET}-clang"
    export AR="${TOOLCHAIN}/bin/llvm-ar"
    export RANLIB="${TOOLCHAIN}/bin/llvm-ranlib"
    export STRIP="${TOOLCHAIN}/bin/llvm-strip"

    log_info "CC = $CC"
}

# ---------------------------------------------------------------------------
# Step 5: Detect include paths and set compiler flags
# ---------------------------------------------------------------------------
detect_includes() {
    local flags=""

    for d in \
        "$SELINUX_SRC/libsepol/include" \
        "$SELINUX_SRC/libsepol/src" \
        "$SELINUX_SRC/libsepol/cil/include" \
        "$SELINUX_SRC/libsepol/cil/src" \
        "$GEN_DIR" \
        "$SELINUX_SRC/libselinux/include"
    do
        if [ -d "$d" ]; then
            flags="$flags -I$d"
        fi
    done

    export INCLUDE_FLAGS="$flags"

    export BASE_CFLAGS="\
        -O2 \
        -fPIE \
        -D_GNU_SOURCE \
        -DANDROID \
        -DDISABLE_SETRANS \
        -DDISABLE_BOOL \
        -DHAVE_REALLOCARRAY \
        -Wno-error \
        -Wno-unused-parameter \
        -Wno-sign-compare \
        -Wno-missing-field-initializers \
        -Wno-visibility \
        -Wno-implicit-function-declaration \
        $INCLUDE_FLAGS"

    # No -pie in LDFLAGS for static executables with lld
    # -static already implies position-independent for our purposes
    export LDFLAGS="-static"

    log_info "Includes: $INCLUDE_FLAGS"
}

# ---------------------------------------------------------------------------
# Helper: compile one file
# ---------------------------------------------------------------------------
compile_file() {
    local src="$1"
    local obj="$2"
    mkdir -p "$(dirname "$obj")"
    if $CC $BASE_CFLAGS -c "$src" -o "$obj" 2>/tmp/ce; then
        return 0
    else
        if [ "${VERBOSE:-0}" = "1" ]; then
            cat /tmp/ce
        fi
        return 1
    fi
}

# ---------------------------------------------------------------------------
# Step 6: Generate CIL lexer from flex source
#
# WHY: cil_lexer.l is a flex grammar file. The .c output (cil_lexer.l.c)
# is NOT in the git repo — it must be generated at build time using flex.
# The symbols cil_lexer_setup / cil_lexer_next / cil_lexer_destroy are
# defined in the generated file. Without generating it, the link fails.
# ---------------------------------------------------------------------------
generate_cil_lexer() {
    log_info "Generating CIL lexer from cil_lexer.l (flex)..."

    local LEX_SRC="$SELINUX_SRC/libsepol/cil/src/cil_lexer.l"
    local LEX_OUT="$GEN_DIR/cil_lexer.l.c"

    mkdir -p "$GEN_DIR"

    if [ ! -f "$LEX_SRC" ]; then
        log_error "cil_lexer.l not found at $LEX_SRC"
        log_error "cil/src contents:"
        ls "$SELINUX_SRC/libsepol/cil/src/" || true
        exit 1
    fi

    log_info "flex input: $LEX_SRC"

    # flex generates the lexer C file
    # --nounistd: avoid unistd.h issues on some cross-compile environments
    # -o: output file path
    if ! flex --nounistd -o "$LEX_OUT" "$LEX_SRC" 2>&1; then
        log_error "flex failed to generate cil_lexer.l.c"
        exit 1
    fi

    if [ ! -f "$LEX_OUT" ]; then
        log_error "flex ran but $LEX_OUT was not created"
        exit 1
    fi

    local size
    size=$(wc -l < "$LEX_OUT")
    log_info "Generated cil_lexer.l.c ($size lines)"
}

# ---------------------------------------------------------------------------
# Step 7: Build libsepol
# ---------------------------------------------------------------------------
build_libsepol() {
    log_info "Building libsepol..."

    local LIBSEPOL_SRC="$SELINUX_SRC/libsepol"
    mkdir -p "$OBJ_DIR/libsepol" "$LIB_DIR"

    # Collect production .c files from libsepol/src/ and libsepol/cil/src/
    # Exclude test and fuzz directories
    local all_c_files=()
    while IFS= read -r -d '' f; do
        all_c_files+=("$f")
    done < <(
        find \
            "$LIBSEPOL_SRC/src" \
            "$LIBSEPOL_SRC/cil/src" \
            -maxdepth 1 \
            -name "*.c" \
            -not -name "*test*" \
            -not -name "*fuzz*" \
            -print0 2>/dev/null | sort -z
    )

    # Add the generated lexer — it is in GEN_DIR, not in the source tree
    local LEX_OUT="$GEN_DIR/cil_lexer.l.c"
    if [ -f "$LEX_OUT" ]; then
        all_c_files+=("$LEX_OUT")
        log_info "Added generated lexer: cil_lexer.l.c"
    else
        log_error "Generated lexer not found at $LEX_OUT — was generate_cil_lexer() called?"
        exit 1
    fi

    log_info "Total source files (including generated): ${#all_c_files[@]}"

    local ok=0 failed=0
    local obj_files=()
    local first_failed_src=""

    for src in "${all_c_files[@]}"; do
        # Build object path: strip any leading path prefix, sanitize separators
        local basename
        basename=$(basename "$src")
        local obj="$OBJ_DIR/libsepol/${basename}.o"

        if compile_file "$src" "$obj"; then
            obj_files+=("$obj")
            ok=$((ok + 1))
        else
            log_warn "Failed: $basename"
            [ -z "$first_failed_src" ] && first_failed_src="$src"
            failed=$((failed + 1))
        fi
    done

    log_info "libsepol: $ok OK, $failed failed out of ${#all_c_files[@]}"

    if [ ${#obj_files[@]} -eq 0 ]; then
        log_error "Zero object files produced"
        $CC $BASE_CFLAGS -c "${all_c_files[0]}" -o /dev/null 2>&1 || true
        exit 1
    fi

    # Allow a small number of failures (e.g. platform-specific files)
    # but fail if more than 10% fail
    local total=${#all_c_files[@]}
    if [ $total -gt 0 ] && [ $((failed * 100 / total)) -gt 10 ]; then
        log_error "Over 10% of files failed ($failed/$total)"
        if [ -n "$first_failed_src" ]; then
            log_error "First failure — full compiler output:"
            $CC $BASE_CFLAGS -c "$first_failed_src" -o /dev/null 2>&1 || true
        fi
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

    local SECILC_DIR="$SELINUX_SRC/secilc"
    local main_src="$SECILC_DIR/secilc.c"

    if [ ! -f "$main_src" ]; then
        log_error "secilc.c not found at $main_src"
        ls "$SECILC_DIR/" || true
        exit 1
    fi

    mkdir -p "$OBJ_DIR/secilc"
    local main_obj="$OBJ_DIR/secilc/secilc.o"

    log_info "Compiling secilc.c..."
    if ! $CC $BASE_CFLAGS -c "$main_src" -o "$main_obj" 2>&1; then
        log_error "secilc.c failed to compile — full error:"
        $CC $BASE_CFLAGS -c "$main_src" -o /dev/null 2>&1 || true
        exit 1
    fi

    log_info "Linking secilc..."
    if ! $CC \
        "$main_obj" \
        "$LIB_DIR/libsepol.a" \
        $LDFLAGS \
        -o "$OBJ_DIR/secilc_bin" 2>&1; then
        log_error "secilc link failed"
        exit 1
    fi

    $STRIP "$OBJ_DIR/secilc_bin"
    cp "$OBJ_DIR/secilc_bin" "$OUTPUT_DIR/secilc"
    chmod +x "$OUTPUT_DIR/secilc"

    local size
    size=$(du -sh "$OUTPUT_DIR/secilc" | cut -f1)
    log_info "secilc: $size → $OUTPUT_DIR/secilc"
}

# ---------------------------------------------------------------------------
# Step 9: Build sepolicy-analyze
# ---------------------------------------------------------------------------
build_sepolicy_analyze() {
    log_info "Building sepolicy-analyze..."

    # Android 12+: system/sepolicy/tools/sepolicy-analyze/
    # Older AOSP:  external/selinux/sepolicy-analyze/
    local SA_DIR=""

    for candidate in \
        "$SEPOLICY_SRC/tools/sepolicy-analyze" \
        "$SELINUX_SRC/sepolicy-analyze" \
        "$SELINUX_SRC/tools/sepolicy-analyze"
    do
        if [ -d "$candidate" ] && \
           find "$candidate" -maxdepth 1 -name "*.c" -quit 2>/dev/null | grep -q .; then
            SA_DIR="$candidate"
            log_info "sepolicy-analyze source: $SA_DIR"
            break
        fi
    done

    # Deep search as last resort
    if [ -z "$SA_DIR" ]; then
        log_warn "Standard locations not found, deep searching..."
        local found
        found=$(find "$SEPOLICY_SRC" "$SELINUX_SRC" \
            -name "sepolicy-analyze.c" \
            -exec dirname {} \; 2>/dev/null | head -1)
        if [ -n "$found" ]; then
            SA_DIR="$found"
            log_info "Found via deep search: $SA_DIR"
        fi
    fi

    if [ -z "$SA_DIR" ]; then
        log_error "Cannot find sepolicy-analyze source"
        log_error "system/sepolicy/tools contents:"
        ls "$SEPOLICY_SRC/tools/" 2>/dev/null || true
        log_error "Searching for *analyze*.c:"
        find "$SEPOLICY_SRC" "$SELINUX_SRC" \
            -name "*analyze*.c" 2>/dev/null | head -10 || true
        exit 1
    fi

    log_debug "sepolicy-analyze files:"
    find "$SA_DIR" -maxdepth 1 -name "*.c" \
        -exec basename {} \; 2>/dev/null | tr '\n' ' '; echo

    local c_files=()
    while IFS= read -r -d '' f; do
        c_files+=("$f")
    done < <(find "$SA_DIR" -maxdepth 1 -name "*.c" \
        -not -name "*test*" \
        -print0 2>/dev/null)

    if [ ${#c_files[@]} -eq 0 ]; then
        log_error "No .c files in $SA_DIR"
        exit 1
    fi

    log_info "sepolicy-analyze: ${#c_files[@]} source files"

    # sepolicy-analyze also needs libsepol includes
    local SA_EXTRA_INCLUDES=""
    if [ -d "$SEPOLICY_SRC/tools/sepolicy-analyze" ]; then
        SA_EXTRA_INCLUDES="-I$SEPOLICY_SRC/tools/sepolicy-analyze"
    fi

    mkdir -p "$OBJ_DIR/sepolicy_analyze"
    local obj_files=()
    local failed=0

    for src in "${c_files[@]}"; do
        local obj="$OBJ_DIR/sepolicy_analyze/$(basename "$src").o"
        mkdir -p "$(dirname "$obj")"
        if $CC $BASE_CFLAGS $SA_EXTRA_INCLUDES -c "$src" -o "$obj" 2>/tmp/ce; then
            obj_files+=("$obj")
        else
            log_warn "Failed: $(basename "$src")"
            cat /tmp/ce | head -20 || true
            failed=$((failed + 1))
        fi
    done

    if [ ${#obj_files[@]} -eq 0 ]; then
        log_error "No objects produced for sepolicy-analyze"
        exit 1
    fi

    log_info "Linking sepolicy-analyze ($((${#obj_files[@]})) objects)..."
    if ! $CC \
        "${obj_files[@]}" \
        "$LIB_DIR/libsepol.a" \
        $LDFLAGS \
        -o "$OBJ_DIR/sepolicy_analyze_bin" 2>&1; then
        log_error "sepolicy-analyze link failed"
        exit 1
    fi

    $STRIP "$OBJ_DIR/sepolicy_analyze_bin"
    cp "$OBJ_DIR/sepolicy_analyze_bin" "$OUTPUT_DIR/sepolicy-analyze"
    chmod +x "$OUTPUT_DIR/sepolicy-analyze"

    local size
    size=$(du -sh "$OUTPUT_DIR/sepolicy-analyze" | cut -f1)
    log_info "sepolicy-analyze: $size → $OUTPUT_DIR/sepolicy-analyze"
}

# ---------------------------------------------------------------------------
# Step 10: Verify
# ---------------------------------------------------------------------------
verify_binaries() {
    log_info "=== Verification ==="
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

        if echo "$info" | grep -qE "aarch64|ARM aarch64|ELF 64-bit.*ARM"; then
            log_info "OK: $binary ($size) — aarch64 ELF"
        else
            log_error "FAIL: $binary — $info"
            all_ok=false
        fi
    done

    if [ "$all_ok" = false ]; then
        exit 1
    fi

    log_info "All binaries verified"
    ls -lh "$OUTPUT_DIR/"
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
main() {
    log_info "==========================================="
    log_info "SELinux Policy Tools — ARM64 Static Build"
    log_info "AOSP tag:  $AOSP_SELINUX_TAG"
    log_info "NDK:       $NDK_VERSION (API $NDK_API)"
    log_info "Output:    $OUTPUT_DIR"
    log_info "==========================================="

    mkdir -p "$OUTPUT_DIR" "$BUILD_DIR" "$OBJ_DIR" "$LIB_DIR" "$GEN_DIR"

    install_ndk
    clone_sources
    inspect_source
    setup_toolchain
    detect_includes
    generate_cil_lexer   # Must run BEFORE build_libsepol
    build_libsepol
    build_secilc
    build_sepolicy_analyze
    verify_binaries

    log_info "==========================================="
    log_info "Build complete!"
    log_info "==========================================="
}

main "$@"
