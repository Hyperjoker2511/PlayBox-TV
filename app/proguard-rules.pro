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
# NOTE: still unverified by a real release build (no builds until the TV
# test is possible, per the user's instruction). The next CI release
# build is the verification: if R8 strips something Media3 needs at
# runtime, add a narrow -keep for that class here — not a blanket one.
