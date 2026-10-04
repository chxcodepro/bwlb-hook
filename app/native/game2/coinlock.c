#include <stdint.h>
#include <stddef.h>
#include <stdatomic.h>

struct phdr_info { uintptr_t address; const char *name; const void *headers; uint16_t count; };
extern int dl_iterate_phdr(int (*callback)(struct phdr_info *, size_t, void *), void *);
extern int mprotect(void *, size_t, int);
extern int getpagesize(void);

/* Verified layout of libgame.so 5.2.6 / 821 (ARM64). */
#define OFF_SHARED_CONTEXT 0xb68058u
#define OFF_USERS_SLOT     0x30u
#define OFF_BASE_VALUE     0x580u
#define OFF_SAVE_USERS     0x340858u
#define OFF_GOLD_GETTER    0x21eb40u
#define OFF_GOLD_SETTER    0x21bbacu
#define OFF_GOLD_SAFE      0x21ea34u
#define OFF_HUD_SHOW       0x243378u
#define OFF_GO_BUY         0x21ebccu
#define OFF_CARROT_UNLOCK  0x33f54cu
#define OFF_STAGE_GET      0x42b510u
#define OFF_NODE_CHILDREN  0x421804u
#define OFF_ARRAY_COUNT    0x429dc4u
#define OFF_ARRAY_OBJECT   0x429de4u
#define HUD_VTABLE         0xaed4a0u

/* Flags bit set shared with the Java layer. */
#define FLAG_GOLD    1
#define FLAG_DIAMOND 2
#define FLAG_PROPS   4
#define FLAG_SHOP    8
#define FLAG_CARROT  16

/* Status bits returned by onFrame / state. */
#define ST_SUPPORTED 1
#define ST_READY     2
#define ST_GOLD      4
#define ST_SHOP      8
#define ST_CARROT    16
#define ST_ERROR     32

#define LEVEL_MAX 139
#define DIAMOND_TOP 999999
#define PROP_TOP 999

static uintptr_t base;
static int supported, gold_patched, gold_value;
static int shop_state, carrot_state;
static int retry_frames;
static _Atomic int64_t last_balance = -1;
static _Atomic int64_t last_diamond = -1;

static const uint8_t original_getter[40] = {
    0xf3,0x0f,0x1e,0xf8,0xfd,0x7b,0x01,0xa9,0xfd,0x43,0x00,0x91,
    0x08,0xc4,0x42,0xf9,0x09,0x78,0x43,0xf9,0x0a,0x7c,0x43,0xf9,
    0xf3,0x03,0x00,0xaa,0x00,0x01,0x09,0xcb,0x48,0x8d,0x01,0x91,0x1f,0x00,0x08,0xeb
};
static const uint8_t original_setter[4] = {0xf4,0x4f,0xbe,0xa9};
static const uint8_t safe_signature[16] = {0xf3,0x0f,0x1e,0xf8,0xfd,0x7b,0x01,0xa9,0xfd,0x43,0x00,0x91,0x08,0xc4,0x42,0xf9};
static const uint8_t show_signature[16] = {0xf3,0x0f,0x1e,0xf8,0xfd,0x7b,0x01,0xa9,0xfd,0x43,0x00,0x91,0x13,0xf8,0x40,0xf9};

/* DataModel::goBuy(node, type) -> deliver the product immediately.
 * The shop node slot is cleared on purpose: buyOver dereferences it as a
 * ShopDlg for UI refresh, and a stale or foreign pointer crashes there. The
 * reward is granted before any of those calls, so delivery stays intact. */
static const uint8_t go_buy_original[24] = {
    0xff,0x03,0x02,0xd1,0xf9,0x1b,0x00,0xf9,0xf8,0x5f,0x04,0xa9,
    0xf6,0x57,0x05,0xa9,0xf4,0x4f,0x06,0xa9,0xfd,0x7b,0x07,0xa9
};
static const uint8_t go_buy_patched[24] = {
    0x02,0x18,0x07,0xb9,  /* str w2, [x0, #0x718]  pending product */
    0x1f,0x90,0x03,0xf9,  /* str xzr, [x0, #0x720] no shop node to refresh */
    0x23,0x00,0x80,0x52,  /* mov w3, #1 */
    0x03,0x08,0x01,0xb9,  /* str w3, [x0, #0x108]  billing flag */
    0xe1,0x03,0x02,0xaa,  /* mov x1, x2            type -> arg */
    0xb3,0x03,0x00,0x14   /* b DataModel::buyOver  */
};

/* UsersData::isUnlockCarrot(CarrotType) -> return true */
static const uint8_t carrot_original[8] = {0xfd,0x7b,0xbf,0xa9,0xfd,0x03,0x00,0x91};
static const uint8_t carrot_patched[8] = {0x20,0x00,0x80,0x52,0xc0,0x03,0x5f,0xd6};

/* Counter fields are copy-on-write: value = *[users+field] - [users+0x580]. */
struct counter { uintptr_t field; uintptr_t adder; };
static const struct counter counters[] = {
    {0x588, 0x3461d0},  /* diamond      */
    {0x608, 0x34641c},  /* star         */
    {0x558, 0x34c240},  /* candy        */
    {0x560, 0x34c384},  /* revitalizer  */
    {0x568, 0x34c4bc},  /* lottery      */
    {0x5e0, 0x340020},  /* clearBB      */
    {0x5e8, 0x340158},  /* carrotHP     */
    {0x5f0, 0x340290},  /* shootSP      */
    {0x5f8, 0x3403c8},  /* stopBB       */
    {0x600, 0x340500},  /* slowBB       */
};
#define COUNTER_COUNT (sizeof(counters)/sizeof(counters[0]))
#define DIAMOND_INDEX 0
#define FIRST_PROP 2

static const uintptr_t level_fields[] = {0x598, 0x540, 0x5b0, 0x5c4, 0x7ac, 0x7b8};
static const uintptr_t skin_fields[] = {0x660, 0x664, 0x668, 0x66c, 0x670, 0x674, 0x678};
static const uintptr_t bag_fields[] = {0x7c0, 0x7c4, 0x7c8, 0x7cc, 0x7d0, 0x7d4};

static int equal(const void *a, const void *b, size_t n) {
    const uint8_t *x=a,*y=b; for(size_t i=0;i<n;i++)if(x[i]!=y[i])return 0; return 1;
}

static void copy(void *dst, const void *src, size_t n) {
    uint8_t *d=dst; const uint8_t *s=src; for(size_t i=0;i<n;i++)d[i]=s[i];
}

static int module_callback(struct phdr_info *info, size_t size, void *unused) {
    (void)size; (void)unused;
    const char *last=info->name,*p=info->name;
    if(!p)return 0;
    while(*p){if(*p=='/')last=p+1;p++;}
    if(equal(last,"libgame.so",11)){base=info->address;return 1;} return 0;
}

static void make_getter(uint32_t *w, int value) {
    w[0]=0xf9400000u|((0x6f0/8)<<10)|9;
    w[1]=0xd2800000u|((value&65535)<<5)|8;
    w[2]=0xf2a00000u|((value>>16)<<5)|8;
    w[3]=0x8b080129u;
    w[4]=0xf9000000u|((0x588/8)<<10)|9;
    w[5]=0xd1000000u|(99<<10)|(8<<5)|8;
    w[6]=0xf9000000u|((0x6f8/8)<<10)|8;
    w[7]=0xd2800000u|((value&65535)<<5);
    w[8]=0xf2a00000u|((value>>16)<<5);
    w[9]=0xd65f03c0u;
}

static int recognized_getter(void) {
    const uint32_t *w=(void *)(base+OFF_GOLD_GETTER); uint32_t expected[10];
    int v=(int)(((w[1]>>5)&65535)|(((w[2]>>5)&65535)<<16));
    if(v<99)return 0;make_getter(expected,v);return equal(w,expected,40);
}

static void flush(void *address, size_t n) {
    uintptr_t ctr; __asm__ volatile("mrs %0, ctr_el0":"=r"(ctr));
    uintptr_t dc=4u<<((ctr>>16)&15),ic=4u<<(ctr&15),start=(uintptr_t)address,end=start+n;
    for(uintptr_t p=start&~(dc-1);p<end;p+=dc)__asm__ volatile("dc cvau, %0"::"r"(p):"memory");
    __asm__ volatile("dsb ish":::"memory");
    for(uintptr_t p=start&~(ic-1);p<end;p+=ic)__asm__ volatile("ic ivau, %0"::"r"(p):"memory");
    __asm__ volatile("dsb ish\nisb":::"memory");
}

static int write_code(uintptr_t address, const void *bytes, size_t length) {
    size_t page=(size_t)getpagesize();uintptr_t start=address&~(page-1);
    size_t size=((address+length+page-1)&~(page-1))-start;
    if(mprotect((void *)start,size,7))return 0;
    copy((void *)address,bytes,length);flush((void *)address,length);
    int restored=mprotect((void *)start,size,5)==0;
    return restored&&equal((void *)address,bytes,length);
}

/* Apply or restore a patch only when the bytes are exactly what we expect. */
static int set_patch(uintptr_t offset, const uint8_t *original, const uint8_t *patched,
                     size_t length, int *state, int on) {
    void *address=(void *)(base+offset);
    if(on) {
        if(equal(address,patched,length)) { *state=1; return 1; }
        if(!equal(address,original,length)) { *state=0; return 0; }
        if(!write_code((uintptr_t)address,patched,length)) { *state=0; return 0; }
        *state=1; return 1;
    }
    if(equal(address,patched,length)&&!write_code((uintptr_t)address,original,length))return 0;
    *state=0; return 1;
}

static int initialize(void) {
    if(supported)return supported;
    if(!base)dl_iterate_phdr(module_callback,0);
    if(!base)return 0;
    uint32_t ret=0xd65f03c0;
    int getter=equal((void *)(base+OFF_GOLD_GETTER),original_getter,40)||recognized_getter();
    int setter=equal((void *)(base+OFF_GOLD_SETTER),original_setter,4)||equal((void *)(base+OFF_GOLD_SETTER),&ret,4);
    supported=getter&&setter&&equal((void *)(base+OFF_GOLD_SAFE),safe_signature,16)
        &&equal((void *)(base+OFF_HUD_SHOW),show_signature,16)?1:-1;
    if(supported==1)gold_patched=recognized_getter();
    return supported;
}

typedef void *(*node_function)(void *);
static void *find_hud(void *node, int depth, int *visited) {
    if(!node||depth>8||++*visited>1200)return 0;
    if(*(uintptr_t *)node==base+HUD_VTABLE)return node;
    void *array=((node_function)(base+OFF_NODE_CHILDREN))(node);
    if(!array)return 0;
    unsigned long n=((unsigned long(*)(void *))(base+OFF_ARRAY_COUNT))(array);if(n>150)n=150;
    for(unsigned long i=0;i<n;i++){
        void *child=((void *(*)(void *,unsigned long))(base+OFF_ARRAY_OBJECT))(array,i);
        void *found=find_hud(child,depth+1,visited);if(found)return found;
    }
    return 0;
}

static void *hud_for(void) {
    void *director=((void *(*)(void))(base+OFF_STAGE_GET))();if(!director)return 0;
    void *root=*(void **)((uint8_t *)director+0xd0);if(!root)return 0;
    int visited=0;return find_hud(root,0,&visited);
}

static int64_t raw_balance(void *data) {
    return *(int64_t *)((uint8_t *)data+0x588)-*(int64_t *)((uint8_t *)data+0x6f0);
}

static void *data_model(void) { return *(void **)(base+OFF_SHARED_CONTEXT); }

static void *users_data(void) {
    void *model=data_model(); if(!model)return 0;
    return *(void **)((uint8_t *)model+OFF_USERS_SLOT);
}

static int64_t read_counter(void *users, uintptr_t field) {
    void *cell=*(void **)((uint8_t *)users+field); if(!cell)return 0;
    int64_t value=*(int64_t *)cell-*(int64_t *)((uint8_t *)users+OFF_BASE_VALUE);
    return value>0?value:0;
}

static void add_counter(void *users, uintptr_t adder, int64_t delta, int save) {
    if(delta==0)return;
    ((int64_t(*)(void *,int64_t,int))(base+adder))(users,delta,save);
}

static void top_up(void *users, uintptr_t field, uintptr_t adder, int64_t floor) {
    int64_t current=read_counter(users,field);
    if(current>=floor)return;
    add_counter(users,adder,floor-current,1);
}

static void unlock_all(void *users) {
    for(size_t i=0;i<COUNTER_COUNT;i++) {
        int64_t floor=i==DIAMOND_INDEX?DIAMOND_TOP:(i==1?9999:PROP_TOP);
        top_up(users,counters[i].field,counters[i].adder,floor);
    }
    for(size_t i=0;i<sizeof(level_fields)/sizeof(level_fields[0]);i++) {
        uintptr_t field=level_fields[i];
        if(field==0x5c4||field==0x7ac||field==0x7b8) *(int32_t *)((uint8_t *)users+field)=LEVEL_MAX+0xa7;
        else *(int64_t *)((uint8_t *)users+field)=LEVEL_MAX+0xa7;
    }
    for(size_t i=0;i<sizeof(skin_fields)/sizeof(skin_fields[0]);i++)
        *(int32_t *)((uint8_t *)users+skin_fields[i])=1;
    for(size_t i=0;i<sizeof(bag_fields)/sizeof(bag_fields[0]);i++)
        *(int32_t *)((uint8_t *)users+bag_fields[i])=1;
    *(int32_t *)((uint8_t *)users+0x698)=1;   /* no ads        */
    *(int32_t *)((uint8_t *)users+0x69c)=1;   /* endless lives */
    ((void(*)(void *))(base+OFF_SAVE_USERS))(users);
}

static int gold_frame(int flags, int value) {
    void *data=data_model();
    if(!data)return 0;
    int64_t balance=raw_balance(data);
    int64_t mirror=*(int64_t *)((uint8_t *)data+0x6f8)+99;
    if(!(flags&FLAG_GOLD)) {
        if(gold_patched) {
            uint32_t ret=0xd65f03c0;
            if(equal((void *)(base+OFF_GOLD_SETTER),&ret,4)
               &&!write_code(base+OFF_GOLD_SETTER,original_setter,4))return -5;
            if(!write_code(base+OFF_GOLD_GETTER,original_getter,40))return -5;
            gold_patched=0;gold_value=0;
        }
        atomic_store(&last_balance,balance);
        return 0;
    }
    if(balance!=mirror)return -3;
    if(value<99||value>2147483647)return -1;
    if(!gold_patched||gold_value!=value) {
        if(retry_frames>0){retry_frames--;return -2;}
        void *hud=hud_for();if(!hud){retry_frames=29;return -2;}
        uint8_t old_getter[40],old_setter[4];
        copy(old_getter,(void *)(base+OFF_GOLD_GETTER),40);
        copy(old_setter,(void *)(base+OFF_GOLD_SETTER),4);
        uint32_t words[10],ret=0xd65f03c0;make_getter(words,value);
        if(!write_code(base+OFF_GOLD_SETTER,&ret,4)||!write_code(base+OFF_GOLD_GETTER,words,40)){
            write_code(base+OFF_GOLD_GETTER,old_getter,40);
            write_code(base+OFF_GOLD_SETTER,old_setter,4);
            return -5;
        }
        gold_patched=1;gold_value=value;
        balance=((int64_t(*)(void *))(base+OFF_GOLD_GETTER))(data);
        ((void(*)(void *,unsigned long))(base+OFF_HUD_SHOW))(hud,(unsigned long)balance);
    } else {
        balance=((int64_t(*)(void *))(base+OFF_GOLD_GETTER))(data);
    }
    atomic_store(&last_balance,balance);
    return 1;
}

__attribute__((visibility("default"))) int Java_io_github_carrot_coinlock_NativeBridge_onFrame(
        void *env,void *type,int flags,int gold,int diamond,int props) {
    (void)env;(void)type;
    int ready=initialize();
    if(ready<0)return ST_ERROR;
    if(!ready)return 0;
    void *users=users_data();
    if(!users){atomic_store(&last_diamond,-1);atomic_store(&last_balance,-1);return ST_SUPPORTED;}

    atomic_store(&last_diamond,read_counter(users,counters[DIAMOND_INDEX].field));
    if(flags&FLAG_DIAMOND&&diamond>0)
        top_up(users,counters[DIAMOND_INDEX].field,counters[DIAMOND_INDEX].adder,(int64_t)diamond);
    if(flags&FLAG_PROPS&&props>0)
        for(size_t i=FIRST_PROP;i<COUNTER_COUNT;i++)
            top_up(users,counters[i].field,counters[i].adder,(int64_t)props);

    int status=ST_SUPPORTED|ST_READY;
    set_patch(OFF_GO_BUY,go_buy_original,go_buy_patched,sizeof(go_buy_original),
              &shop_state,(flags&FLAG_SHOP)?1:0);
    if(shop_state)status|=ST_SHOP;
    set_patch(OFF_CARROT_UNLOCK,carrot_original,carrot_patched,sizeof(carrot_original),
              &carrot_state,(flags&FLAG_CARROT)?1:0);
    if(carrot_state)status|=ST_CARROT;
    int coins=gold_frame(flags,gold);
    /* -2/-3 just mean "no battle running yet"; only real failures are reported. */
    if(coins==-1||coins==-5)status|=ST_ERROR;
    else if(coins>0)status|=ST_GOLD;
    return status;
}

__attribute__((visibility("default"))) int64_t Java_io_github_carrot_coinlock_NativeBridge_balance(void *env,void *type) {
    (void)env;(void)type;return atomic_load(&last_balance);
}

__attribute__((visibility("default"))) int64_t Java_io_github_carrot_coinlock_NativeBridge_diamond(void *env,void *type) {
    (void)env;(void)type;return atomic_load(&last_diamond);
}

__attribute__((visibility("default"))) int Java_io_github_carrot_coinlock_NativeBridge_unlock(void *env,void *type) {
    (void)env;(void)type;
    if(initialize()!=1)return 0;
    void *users=users_data();if(!users)return 0;
    unlock_all(users);return 1;
}
