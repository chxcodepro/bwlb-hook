#include <stdint.h>
#include <stddef.h>
#include <stdatomic.h>

struct phdr_info { uintptr_t address; const char *name; const void *headers; uint16_t count; };
extern int dl_iterate_phdr(int (*callback)(struct phdr_info *,size_t,void *),void *);
extern int mprotect(void *,size_t,int);
extern int getpagesize(void);
#define GET 0xa73ea8u
#define CHANGE 0xa7ae58u
#define SAFE 0xa7ad20u
#define SHOW 0x7921d4u
#define ENCODE 0x54cab8u
#define CONTEXT 0x1ae66f0u
#define VTABLE 0x1a1c388u
#define TOP 999999
#define USERDEFAULT 0xaa0d18u
#define POINT_KEY 0x14c686fu
#define POINT_SITES 17u
static uintptr_t base;
static int supported,patched,retry;
static void *last_scene;
static _Atomic int64_t last_balance=-1;
static _Atomic int64_t wallet_balance[3]={-1,-1,-1};
static int wallet_frames,wallet_mask;
static const uint32_t fixed_wallet[3]={0x528847e0,0x72a001e0,0xd65f03c0};
static const uintptr_t wallet_getters[2]={0xb7234c,0xb7583c};
static const uintptr_t wallet_setters[2]={0xb7240c,0xb75874};
static const uint8_t wallet_original[2][32]={
{0xf3,0x0f,0x1e,0xf8,0xfd,0x7b,0x01,0xa9,0xfd,0x43,0x00,0x91,0x70,0xba,0xfc,0x97,0xe1,0x4c,0x00,0xf0,0x21,0x18,0x15,0x91,0xe2,0x03,0x1f,0x2a,0x0b,0xbe,0xfc,0x97},
{0xf3,0x0f,0x1e,0xf8,0xfd,0x7b,0x01,0xa9,0xfd,0x43,0x00,0x91,0x34,0xad,0xfc,0x97,0xf3,0x03,0x00,0xaa,0x00,0x0c,0x8b,0x52,0x20,0x00,0xa0,0x72,0xca,0xb0,0xfa,0x97}};
static const uint8_t wallet_set_original[2][16]={
{0xff,0x03,0x01,0xd1,0xf4,0x4f,0x02,0xa9,0xfd,0x7b,0x03,0xa9,0xfd,0xc3,0x00,0x91},
{0xf4,0x4f,0xbe,0xa9,0xfd,0x7b,0x01,0xa9,0xfd,0x43,0x00,0x91,0xf3,0x03,0x01,0x2a}};
static const uint8_t original_get[80]={
0xf3,0x0f,0x1e,0xf8,0xfd,0x7b,0x01,0xa9,0xfd,0x43,0x00,0x91,0xf3,0x03,0x00,0xaa,
0x9a,0x1b,0x00,0x94,0x68,0x8e,0x42,0xf9,0xfd,0x7b,0x41,0xa9,0x09,0x81,0x40,0x39,
0x0a,0x85,0x40,0x39,0x08,0x09,0x09,0x8b,0x0b,0x05,0x40,0x39,0x09,0x01,0x40,0x39,
0x0c,0x09,0x40,0x39,0x08,0x0d,0x40,0x39,0x6b,0x01,0x0a,0x4a,0x29,0x01,0x0a,0x4a,
0x8c,0x01,0x0a,0x4a,0x6b,0x1d,0x00,0x12,0x08,0x01,0x0a,0x4a,0x8a,0x1d,0x00,0x12};
static const uint32_t locked_get[20]={
0xa9be7bf3,0xaa0003f3,0xf9428669,0x528847e8,0x72a001e8,0x0b090108,0xb90013e8,
0xf9428e60,0x910043e1,0x97eb62fb,0x52883b88,0x72a001e8,0xb90013e8,0xf9428a60,
0x910043e1,0x97eb62f5,0x528847e0,0x72a001e0,0xa8c27bf3,0xd65f03c0};
static const uint32_t original_change=0xd10143ff,ret=0xd65f03c0;
static const uint8_t safe_signature[16]={0xff,0xc3,0x00,0xd1,0xf4,0x4f,0x01,0xa9,0xfd,0x7b,0x02,0xa9,0xfd,0x83,0x00,0x91};
static const uint8_t show_signature[16]={0xf3,0x0f,0x1e,0xf8,0xfd,0x7b,0x01,0xa9,0xfd,0x43,0x00,0x91,0x13,0x04,0x41,0xf9};
static const uint8_t encode_signature[16]={0xf5,0x0f,0x1d,0xf8,0xf4,0x4f,0x01,0xa9,0xfd,0x7b,0x02,0xa9,0xfd,0x83,0x00,0x91};
/* 点券 (paid voucher) lives in the server-synced field passportTicket and is
   mirrored into the UserDefault key BattlePassPlayerTicket. Every local
   affordability check is an inlined getIntegerForKey on that literal, so each
   site needs its own patch. */
static const uintptr_t point_sites[POINT_SITES]={
0x4d5df4u,0x73bd60u,0x73bf1cu,0x73c0d0u,0x73c27cu,0x73c41cu,0x73c5c8u,0x73c778u,0x73c960u,
0x8d8940u,0x8da280u,0x8dbe94u,0x8f1d88u,0x985740u,0xac3114u,0xad9fccu,0xb91510u};
static const uint8_t point_originals[POINT_SITES][20]={
{0xc9,0x2b,0x17,0x94,0x81,0x7f,0x00,0xb0,0x21,0xbc,0x21,0x91,0xe2,0x03,0x1f,0x2a,0x64,0x2f,0x17,0x94},
{0xee,0x93,0x0d,0x94,0x41,0x6c,0x00,0xf0,0x21,0xbc,0x21,0x91,0xe2,0x03,0x1f,0x2a,0x89,0x97,0x0d,0x94},
{0x7f,0x93,0x0d,0x94,0x41,0x6c,0x00,0xf0,0x21,0xbc,0x21,0x91,0xe2,0x03,0x1f,0x2a,0x1a,0x97,0x0d,0x94},
{0x12,0x93,0x0d,0x94,0x41,0x6c,0x00,0xd0,0x21,0xbc,0x21,0x91,0xe2,0x03,0x1f,0x2a,0xad,0x96,0x0d,0x94},
{0xa7,0x92,0x0d,0x94,0x41,0x6c,0x00,0xd0,0x21,0xbc,0x21,0x91,0xe2,0x03,0x1f,0x2a,0x42,0x96,0x0d,0x94},
{0x3f,0x92,0x0d,0x94,0x41,0x6c,0x00,0xd0,0x21,0xbc,0x21,0x91,0xe2,0x03,0x1f,0x2a,0xda,0x95,0x0d,0x94},
{0xd4,0x91,0x0d,0x94,0x41,0x6c,0x00,0xd0,0x21,0xbc,0x21,0x91,0xe2,0x03,0x1f,0x2a,0x6f,0x95,0x0d,0x94},
{0x68,0x91,0x0d,0x94,0x41,0x6c,0x00,0xd0,0x21,0xbc,0x21,0x91,0xe2,0x03,0x1f,0x2a,0x03,0x95,0x0d,0x94},
{0xee,0x90,0x0d,0x94,0x41,0x6c,0x00,0xd0,0x21,0xbc,0x21,0x91,0xe2,0x03,0x1f,0x2a,0x89,0x94,0x0d,0x94},
{0xf6,0x20,0x07,0x94,0x61,0x5f,0x00,0xd0,0x21,0xbc,0x21,0x91,0xe2,0x03,0x1f,0x2a,0x91,0x24,0x07,0x94},
{0xa6,0x1a,0x07,0x94,0x61,0x5f,0x00,0x90,0x21,0xbc,0x21,0x91,0xe2,0x03,0x1f,0x2a,0x41,0x1e,0x07,0x94},
{0xa1,0x13,0x07,0x94,0x41,0x5f,0x00,0xf0,0x21,0xbc,0x21,0x91,0xe2,0x03,0x1f,0x2a,0x3c,0x17,0x07,0x94},
{0xe4,0xbb,0x06,0x94,0xa1,0x5e,0x00,0xb0,0x21,0xbc,0x21,0x91,0xe2,0x03,0x1f,0x2a,0x7f,0xbf,0x06,0x94},
{0x76,0x6d,0x04,0x94,0x01,0x5a,0x00,0xb0,0x21,0xbc,0x21,0x91,0xe2,0x03,0x1f,0x2a,0x11,0x71,0x04,0x94},
{0x01,0x77,0xff,0x97,0x01,0x50,0x00,0xf0,0x21,0xbc,0x21,0x91,0xe2,0x03,0x1f,0x2a,0x9c,0x7a,0xff,0x97},
{0x53,0x1b,0xff,0x97,0x61,0x4f,0x00,0xb0,0x21,0xbc,0x21,0x91,0xe2,0x03,0x13,0x2a,0x2e,0x1d,0xff,0x97},
{0x02,0x3e,0xfc,0x97,0xa1,0x49,0x00,0xb0,0x21,0xbc,0x21,0x91,0xe2,0x03,0x14,0x2a,0xdd,0x3f,0xfc,0x97},
};
static const uint32_t point_lock[5]={0x528847e0,0x72a001e0,0xd503201f,0xd503201f,0xd503201f};
static int point_patched;
static int equal(const void *a,const void *b,size_t n){const uint8_t *x=a,*y=b;for(size_t i=0;i<n;i++)if(x[i]!=y[i])return 0;return 1;}
static void copy(void *a,const void *b,size_t n){uint8_t *x=a;const uint8_t *y=b;for(size_t i=0;i<n;i++)x[i]=y[i];}
static int write_code(uintptr_t p,const void *bytes,size_t n);
static int point_count(void){
    int n=0;for(unsigned i=0;i<POINT_SITES;i++)if(equal((void *)(base+point_sites[i]),point_lock,20))n++;
    return n;
}
static int point_intact(void){
    for(unsigned i=0;i<POINT_SITES;i++){
        uintptr_t p=base+point_sites[i];
        if(!equal((void *)p,point_originals[i],20)&&!equal((void *)p,point_lock,20))return 0;
    }
    return 1;
}
static int point_restore(void){
    int ok=1;
    for(unsigned i=0;i<POINT_SITES;i++){
        uintptr_t p=base+point_sites[i];
        if(equal((void *)p,point_lock,20)&&!write_code(p,point_originals[i],20))ok=0;
    }
    if(ok)point_patched=0;
    return ok&&point_count()==0;
}
static int point_apply(void){
    if(point_patched)return 1;
    if(!point_intact())return 0;
    for(unsigned i=0;i<POINT_SITES;i++){
        uintptr_t p=base+point_sites[i];
        if(equal((void *)p,point_lock,20))continue;
        if(!write_code(p,point_lock,20)){point_restore();return 0;}
    }
    point_patched=1;return 1;
}
static int64_t point_read(void){
    void *data=((void *(*)(void))(base+USERDEFAULT))();
    if(!data)return -1;
    return (int64_t)((int (*)(void *,const char *,int))(base+0xaa1b94u))(data,(const char *)(base+POINT_KEY),0);
}
static int module(struct phdr_info *p,size_t n,void *unused){
    (void)n;(void)unused;if(!p->name)return 0;const char *s=p->name,*last=s;
    for(;*s;s++)if(*s=='/')last=s+1;
    const char *name="libgame.so";int i=0;while(name[i]&&last[i]==name[i])i++;
    if(!name[i]&&!last[i]){base=p->address;return 1;}return 0;
}
static void flush(void *address,size_t n){
    uintptr_t ctr;__asm__ volatile("mrs %0, ctr_el0":"=r"(ctr));
    uintptr_t dc=4u<<((ctr>>16)&15),ic=4u<<(ctr&15),start=(uintptr_t)address,end=start+n;
    for(uintptr_t p=start&~(dc-1);p<end;p+=dc)__asm__ volatile("dc cvau, %0"::"r"(p):"memory");
    __asm__ volatile("dsb ish":::"memory");
    for(uintptr_t p=start&~(ic-1);p<end;p+=ic)__asm__ volatile("ic ivau, %0"::"r"(p):"memory");
    __asm__ volatile("dsb ish\nisb":::"memory");
}
static int write_code(uintptr_t p,const void *bytes,size_t n){
    size_t page=(size_t)getpagesize();uintptr_t start=p&~(page-1);size_t length=((p+n+page-1)&~(page-1))-start;
    if(mprotect((void *)start,length,7))return 0;
    copy((void *)p,bytes,n);flush((void *)p,n);
    return mprotect((void *)start,length,5)==0&&equal((void *)p,bytes,n);
}
static int init(void){
    if(supported)return supported;
    if(!base)dl_iterate_phdr(module,0);if(!base)return 0;
    int g=equal((void *)(base+GET),original_get,80)||equal((void *)(base+GET),locked_get,80);
    int c=equal((void *)(base+CHANGE),&original_change,4)||equal((void *)(base+CHANGE),&ret,4);
    int wallet_valid=1;
    for(int i=0;i<2;i++){
        uintptr_t gp=base+wallet_getters[i],sp=base+wallet_setters[i];
        int get=equal((void *)gp,wallet_original[i],32)||(equal((void *)gp,fixed_wallet,12)&&equal((void *)(gp+12),wallet_original[i]+12,20));
        int set=equal((void *)sp,wallet_set_original[i],16)||(equal((void *)sp,&ret,4)&&equal((void *)(sp+4),wallet_set_original[i]+4,12));
        if(!get||!set)wallet_valid=0;
        if(equal((void *)gp,fixed_wallet,12))wallet_mask|=1<<i;
    }
    supported=g&&c&&wallet_valid&&point_intact()&&equal((void *)(base+SAFE),safe_signature,16)&&equal((void *)(base+SHOW),show_signature,16)
        &&equal((void *)(base+ENCODE),encode_signature,16)?1:-1;
    patched=equal((void *)(base+GET),locked_get,80);point_patched=point_count()==(int)POINT_SITES;return supported;
}
static void *field(void *p,uintptr_t off){return *(void **)((uintptr_t)p+off);}
static int decode(void *p,int32_t *result){
    if(!p)return 0;uint8_t *q=p,i=q[32],k=q[33];if(i>7)return 0;
    uint32_t v=0;for(int j=0;j<4;j++)v|=(uint32_t)(q[i*4+j]^k)<<(8*j);*result=(int32_t)v;return 1;
}
static int balance(void *p,int64_t *value){
    int32_t a,c;if(!decode(field(p,0x518),&a)||!decode(field(p,0x510),&c))return 0;
    *value=(int64_t)a-*(int64_t *)((uintptr_t)p+0x508);return *value==(int64_t)c+99;
}
static int restore(void){
    int a=1,c=1;
    if(equal((void *)(base+GET),locked_get,80))a=write_code(base+GET,original_get,80);
    if(equal((void *)(base+CHANGE),&ret,4))c=write_code(base+CHANGE,&original_change,4);
    if(a&&c){patched=0;last_scene=0;}return a&&c;
}
static int wallet_frame(int flags){
    int wanted=((flags&2)?1:0)|((flags&8)?2:0),changed=0;
    void *context=*(void **)(base+CONTEXT),*data=*(void **)(base+0x1af1bf0u);
    int ready=context&&data&&*(void **)(base+0x1aeb8e0u)&&*(void **)(base+0x1af7900u);
    for(int i=0;i<2;i++){
        int bit=1<<i,on=(wanted&bit)!=0;
        uintptr_t gp=base+wallet_getters[i],sp=base+wallet_setters[i];
        if(on&&!ready)continue;
        if(on&&!(wallet_mask&bit)){
            if(!equal((void *)gp,wallet_original[i],32)||!equal((void *)sp,wallet_set_original[i],16))return 8;
            if(!write_code(sp,&ret,4)||!write_code(gp,fixed_wallet,12)){
                write_code(gp,wallet_original[i],12);write_code(sp,wallet_set_original[i],4);return 8;
            }
            wallet_mask|=bit;changed=1;
        }else if(!on&&(wallet_mask&bit)){
            if(!write_code(gp,wallet_original[i],12)||!write_code(sp,wallet_set_original[i],4))return 8;
            wallet_mask&=~bit;changed=1;
        }
    }
    int point_on=(flags&16)!=0,point_bit=4;
    if(!(wallet_mask&point_bit)&&(point_patched||point_count()==(int)POINT_SITES))wallet_mask|=point_bit;
    if(point_on&&!(wallet_mask&point_bit)){
        if(!point_apply())return 8;
        wallet_mask|=point_bit;changed=1;
    }else if(!point_on&&(wallet_mask&point_bit)){
        if(!point_restore())return 8;
        wallet_mask&=~point_bit;changed=1;
    }
    if(ready&&(changed||wallet_frames++%60==0)){
        for(int i=0;i<2;i++)atomic_store(&wallet_balance[i],((int (*)(void *))(base+wallet_getters[i]))(data));
        /* The voucher lock rewrites inline reads, not a shared getter, so the
           reported balance has to follow the lock state rather than the mirror. */
        int64_t points=point_read();
        if(wallet_mask&4)points=TOP;
        atomic_store(&wallet_balance[2],points);
        if(changed){
            void *center=((void *(*)(void))(base+0x10b0b28u))();
            ((void (*)(void *,const char *))(base+0x10b12bcu))(center,(const char *)(base+0x14c554bu));
        }
    }else if(!ready)for(int i=0;i<3;i++)atomic_store(&wallet_balance[i],-1);
    return (ready?256:0)|((wallet_mask&1)?32:0)|((wallet_mask&2)?128:0)|((wallet_mask&4)?512:0);
}
__attribute__((visibility("default"))) int Java_io_github_carrot3_coinlock_NativeBridge_onFrame(void *env,void *cls,int flags){
    (void)env;(void)cls;
    if(!supported&&retry++%60)return 0;
    int valid=init();if(valid<0)return 16;if(!valid)return 0;
    int wallet_status=wallet_frame(flags);if(wallet_status&8)return 8;
    int enabled=(flags&1)!=0;
    if(!enabled&&patched&&!restore())return 8;
    void *context=*(void **)(base+CONTEXT);
    void *scene=context?field(context,0x108):0;
    if(!scene||*(uintptr_t *)scene!=base+VTABLE){atomic_store(&last_balance,-1);return 1|wallet_status;}
    int64_t current;if(!balance(scene,&current)){atomic_store(&last_balance,-1);return 8;}
    if(enabled){
        if(!patched){
            if(!equal((void *)(base+GET),original_get,80)||!equal((void *)(base+CHANGE),&original_change,4))return 8;
            if(!write_code(base+CHANGE,&ret,4)||!write_code(base+GET,locked_get,80)){restore();return 8;}
            patched=1;
        }
        if(scene!=last_scene||current!=TOP){
            uint8_t a[36],c[36];void *primary=field(scene,0x518),*mirror=field(scene,0x510);
            copy(a,primary,36);copy(c,mirror,36);
            int64_t result=((int64_t (*)(void *))(base+GET))(scene);
            if(result!=TOP||!((int (*)(void *))(base+SAFE))(scene)){
                restore();copy(primary,a,36);copy(mirror,c,36);return 8;
            }
            void *hud=field(scene,0x1f0);
            if(hud)((void (*)(void *,int64_t))(base+SHOW))(hud,TOP);
            last_scene=scene;current=TOP;
        }
    }
    atomic_store(&last_balance,current);return 1|2|(enabled&&patched?4:0)|wallet_status;
}
__attribute__((visibility("default"))) int64_t Java_io_github_carrot3_coinlock_NativeBridge_balance(void *env,void *cls){(void)env;(void)cls;return atomic_load(&last_balance);}
__attribute__((visibility("default"))) int64_t Java_io_github_carrot3_coinlock_NativeBridge_wallet(void *env,void *cls,int index){(void)env;(void)cls;return index>=0&&index<3?atomic_load(&wallet_balance[index]):-1;}
