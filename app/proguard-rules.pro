# PlayBox TV — R8 / ProGuard rules (Batch 1.3).
#
# The old file kept everything (our code + all of media3, compose,
# lifecycle and documentfile), which effectively switched R8 off while
# isMinifyEnabled was true. Those libraries ship their own consumer
# ProGuard rules, so no blanket -keep lines are needed here.
#
# Our own code is all referenced directly — no reflection, no JNI — so
# nothing in com.playbox.tv needs an explicit keep either.
#
# NOTE: verified by the first real release build (CI, 2026-11-22): R8
# caught one missing class (see below) and was fixed with a narrow
# -dontwarn, not a blanket keep. If a future release build fails in
# R8 again, add the narrow rule for that class here.
#
# Batch 2 (1.1.0): jcifs-ng logs through slf4j-api, but no slf4j
# binding ships with it. slf4j-api only references StaticLoggerBinder
# to look for a binding and no-ops without one — R8 still treats the
# missing class as an error, so silence that one warning.
-dontwarn org.slf4j.**
