typedef unsigned long long U64;
typedef unsigned long UP;
typedef int I32;

static UP clean(UP p) { return p & 0x00ffffffffffffffUL; }

__attribute__((visibility("default")))
I32 Java_com_lcs_coinlock_NativeCoin_apply(void *env, void *clazz, U64 address, I32 value) {
    UP base = (UP)address;
    if (!base || value < 0 || value > 9999999) return -1;
    if (*(unsigned int *)(base + 0x226c30) != 0xb940b260U ||
        *(unsigned int *)(base + 0x226b68) != 0xb9418008U) return -1;
    UP game = clean(*(UP *)(base + 0xa4d198));
    if (!game) return 0;
    if (*(UP *)game != base + 0x9bd000) return -1;
    if (!*(UP *)(game + 0x20) || !*(UP *)(game + 0x120)) return 0;
    I32 old = *(I32 *)(game + 0xb0);
    I32 guard = *(I32 *)(game + 0x180);
    if (old < 0 || old > 10000000 || guard != old - 1) return -2;
    if (old != value) {
        typedef void (*AddMoney)(void *, I32);
        ((AddMoney)(base + 0x2236cc))((void *)game, value - old);
    }
    return *(I32 *)(game + 0xb0) == value && *(I32 *)(game + 0x180) == value - 1 ? 1 : -2;
}
