typedef unsigned long usize;
typedef unsigned long uptr;
typedef unsigned int u32;
typedef unsigned short u16;
typedef long long jlong;
typedef struct { u32 type, flags; uptr offset, vaddr, paddr, filesz, memsz, align; } Phdr;
typedef struct { uptr base; const char *name; const Phdr *phdr; u16 phnum; } DlInfo;
extern int dl_iterate_phdr(int (*callback)(DlInfo *, usize, void *), void *);
#include "controller.h"

static uptr base, state_slot;
static int validated, unavailable;
static void *initialized_state;
static jlong coins = -1;
static jlong wallet[6] = {-1, -1, -1, -1, -1, -1};
static int (*gettop)(void *);
static void (*settop)(void *, int);
static void (*getfield)(void *, int, const char *);
static const char *(*pushstring)(void *, const char *);
static int (*pcall)(void *, int, int, int);
static int (*luatype)(void *, int);
static double (*tonumber)(void *, int);

static int suffix(const char *s, const char *end) {
    if (!s) return 0;
    usize a = 0, b = 0;
    while (s[a]) ++a;
    while (end[b]) ++b;
    if (a < b) return 0;
    for (usize i = 0; i < b; ++i) if (s[a - b + i] != end[i]) return 0;
    return 1;
}
static int equal(const unsigned char *p, const unsigned char *q, usize n) {
    for (usize i = 0; i < n; ++i) if (p[i] != q[i]) return 0;
    return 1;
}
static void mask_text(int mask, char *out) {
    int value = mask <= 0 ? 0 : mask;
    char digits[8];
    int count = 0;
    if (value == 0) { out[0] = '0'; out[1] = 0; return; }
    while (value > 0 && count < 8) { digits[count++] = (char)('0' + value % 10); value /= 10; }
    int i = 0;
    while (count > 0) out[i++] = digits[--count];
    out[i] = 0;
}
static int discover(DlInfo *info, usize size, void *data) {
    if (!suffix(info->name, "/libgame.so")) return 0;
    const unsigned char a[] = {0x08,0x24,0x42,0xa9,0x28,0x01,0x08,0xcb,0x00,0xfd,0x43,0xd3,0xc0,0x03,0x5f,0xd6};
    const unsigned char b[] = {0xf4,0x0f,0x1e,0xf8,0xf3,0x7b,0x01,0xa9,0x13,0x08,0x40,0xf9,0xea,0x03,0x00,0xaa};
    const unsigned char c[] = {0xff,0xc3,0x00,0xd1,0xf5,0x53,0x01,0xa9,0xf3,0x7b,0x02,0xa9,0xf4,0x03,0x02,0xaa};
    int text_ok = 0, slot_ok = 0;
    for (u16 i = 0; i < info->phnum; ++i) {
        const Phdr *p = info->phdr + i;
        if (p->type != 1) continue;
        if ((p->flags & 1) && p->vaddr <= 0x119b834 && p->vaddr + p->memsz > 0x11a0118) text_ok = 1;
        if ((p->flags & 2) && p->vaddr <= 0x1335fc8 && p->vaddr + p->memsz >= 0x1335fd0) slot_ok = 1;
    }
    if (!text_ok || !slot_ok ||
        !equal((void *)(info->base + 0x119b834), a, sizeof(a)) ||
        !equal((void *)(info->base + 0x11a0108), b, sizeof(b)) ||
        !equal((void *)(info->base + 0x119ea4c), c, sizeof(c))) {
        unavailable = 1;
        return 1;
    }
    base = info->base;
    state_slot = base + 0x1335fc8;
    gettop = (void *)(base + 0x119b834);
    settop = (void *)(base + 0x119b844);
    getfield = (void *)(base + 0x119ea4c);
    pushstring = (void *)(base + 0x119e298);
    pcall = (void *)(base + 0x11a0108);
    luatype = (void *)(base + 0x119bd2c);
    tonumber = (void *)(base + 0x119caac);
    validated = 1;
    return 1;
}

__attribute__((visibility("default")))
int Java_io_github_carrot4_coinlock_NativeBridge_onFrame(void *env, void *klass, int mask) {
    if (unavailable) return 6;
    if (!validated) dl_iterate_phdr(discover, 0);
    if (!validated) return unavailable ? 6 : 1;
    void *L = *(void **)state_slot;
    if (!L) return 1;
    int top = gettop(L), status = 5;
    if (initialized_state != L) {
        getfield(L, -10002, "loadstring");
        if (luatype(L, -1) != 6) goto done;
        pushstring(L, CONTROLLER);
        if (pcall(L, 1, 1, 0) || pcall(L, 0, 1, 0)) goto done;
        settop(L, top);
        initialized_state = L;
    }
    {
        char text[8];
        mask_text(mask, text);
        getfield(L, -10002, "__carrot4_apply");
        if (luatype(L, -1) != 6) { initialized_state = 0; goto done; }
        pushstring(L, text);
        if (pcall(L, 1, 1, 0) || luatype(L, -1) != 3) goto done;
        double number = tonumber(L, -1);
        if (number < 1 || number > 16000000000.0) goto done;
        jlong packed = (jlong)number;
        status = (int)(packed & 15);
        __atomic_store_n(&coins, packed / 16 - 1, __ATOMIC_RELAXED);
    }
done:
    settop(L, top);
    if (initialized_state == L) {
        getfield(L, -10002, "__carrot4_wallet");
        int valid = luatype(L, -1) == 6 && pcall(L, 0, 6, 0) == 0;
        for (int i = 0; i < 6; ++i) {
            jlong value = -1;
            if (valid && luatype(L, i - 6) == 3) {
                double number = tonumber(L, i - 6);
                if (number >= 0 && number <= 1000000000.0) value = (jlong)number;
            }
            __atomic_store_n(&wallet[i], value, __ATOMIC_RELAXED);
        }
        settop(L, top);
    }
    return status;
}

__attribute__((visibility("default")))
jlong Java_io_github_carrot4_coinlock_NativeBridge_balance(void *env, void *klass) {
    return __atomic_load_n(&coins, __ATOMIC_RELAXED);
}

__attribute__((visibility("default")))
jlong Java_io_github_carrot4_coinlock_NativeBridge_wallet(void *env, void *klass, int index) {
    if (index < 0 || index > 5) return -1;
    return __atomic_load_n(&wallet[index], __ATOMIC_RELAXED);
}
