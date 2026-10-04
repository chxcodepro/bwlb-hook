-- Carrot4 controller: match-coin lock, account wallet locks and shop limit removal.
-- Runs on the game's Lua state from the renderer hook at most once every 300 ms.
local FIXED_COIN = 999999
local FIXED_GEM = 88888
local FIXED_GOLD = 999999
local FIXED_STAMINA = 999

local coinLock, gemLock, goldLock, shopFree, staminaFree = false, false, false, false, false
local bar, barState, shopFixed, shopOpen, staminaInfinite = nil, nil, 0, 0, 0

local function safe(f, ...)
    local ok, result = pcall(f, ...)
    if ok then return result end
    return nil
end

local function riches()
    local model = _data and _data.DataModel
    local list = model and model.richesList
    if type(list) ~= 'table' then return nil end
    return list
end

local function findCname(node, want, depth)
    if not node or depth > 8 then return nil end
    local peer = safe(function() return tolua.getpeer(node) end)
    if type(peer) == 'table' and peer.__cname == want then return node end
    local children = safe(function() return node:getChildren() end)
    if children then
        for i = 1, #children do
            local found = findCname(children[i], want, depth + 1)
            if found then return found end
        end
    end
    return nil
end

local function scene()
    return safe(function() return cc.Director:getInstance():getRunningScene() end)
end

local function topBar()
    if bar and barState then return bar end
    barState = nil
    bar = nil
    local node = scene()
    if not node then return nil end
    local found = safe(function() return findCname(node, 'TopMenuBar', 0) end)
    if found then
        barState = safe(function() return tolua.getpeer(found).__cname end)
        bar = found
    end
    return bar
end

local function gameLayer()
    local node = scene()
    if not node then return nil end
    local game = node.myGameLayer
    if not game or not game.money or not game.getMoney then return nil end
    return game
end

local function refresh(game)
    if game.spTopToolBar and game.spTopToolBar.updateMoney then game.spTopToolBar:updateMoney() end
    if game.chessBoard and game.chessBoard.moneyChangeUpdateInfo then game.chessBoard:moneyChangeUpdateInfo() end
end

local function lockWallet()
    local list = riches()
    if not list then return end
    local dirty = false
    if gemLock and tonumber(list.gem) ~= FIXED_GEM then
        list.gem = FIXED_GEM
        dirty = true
    end
    if goldLock and tonumber(list.gold) ~= FIXED_GOLD then
        list.gold = FIXED_GOLD
        dirty = true
    end
    if dirty then
        local node = topBar()
        if node then pcall(function() return node:updateText() end) end
    end
end

local function stamina()
    local list = riches()
    local phys = list and list.physical
    if type(phys) ~= 'table' then return nil end
    return phys
end

local function lockStamina()
    local phys = stamina()
    if not phys then
        staminaInfinite = 0
        return
    end
    if tonumber(phys.nCount) ~= FIXED_STAMINA then phys.nCount = FIXED_STAMINA end
    if phys.bInfinite ~= true then
        local node = topBar()
        if node then pcall(function() return node:setInfinitePhysical(true) end) end
        if phys.bInfinite ~= true then phys.bInfinite = true end
    end
    staminaInfinite = phys.bInfinite == true and 1 or 0
end

local function unlockStamina()
    local phys = stamina()
    staminaInfinite = 0
    if not phys then return end
    if phys.bInfinite == true then
        local node = topBar()
        if node then pcall(function() return node:setInfinitePhysical(false) end) end
        phys.bInfinite = false
    end
    local model = _data and _data.DataModel
    if model and model.getPhysicalFromLocal then
        local ok, value = pcall(model.getPhysicalFromLocal, model)
        if ok and type(value) == 'number' then phys.nCount = value end
    end
end

local function freeShop()    local node = scene()
    if not node then return end
    local shop = safe(function() return findCname(node, 'StoneShop', 0) end)
    if not shop then
        shopOpen = 0
        return
    end
    shopOpen = 1
    local peer = safe(function() return tolua.getpeer(shop) end)
    if type(peer) ~= 'table' then return end
    local changed = 0
    for _, key in ipairs({'shop_gift_proto', 'shop_gem_proto'}) do
        local proto = peer[key]
        if type(proto) == 'table' then
            for _, item in pairs(proto) do
                if type(item) == 'table' then
                    if tonumber(item.tag or 0) ~= 0 then
                        item.tag = 0
                        changed = changed + 1
                    end
                    if tonumber(item.limitednums or 0) ~= 0 then
                        item.limitednums = 0
                        changed = changed + 1
                    end
                end
            end
        end
    end
    if changed > 0 then shopFixed = shopFixed + changed end
end

local function matchCoin(enabled)
    local game = gameLayer()
    if not game then return 2, 0 end
    local peer = tolua.getpeer(game)
    if not peer then return 2, 0 end
    local state = rawget(peer, '__carrot4_lock')
    if enabled then
        if not state then
            state = {
                original = game.addGameLayerMoney,
                own = rawget(peer, 'addGameLayerMoney'),
                calls = 0
            }
            if type(state.original) ~= 'function' then return 5, 0 end
            rawset(peer, '__carrot4_lock', state)
            rawset(peer, 'addGameLayerMoney', function(self, amount, ...)
                state.calls = state.calls + 1
                local result = state.original(self, amount, ...)
                self.money:setValue(FIXED_COIN)
                refresh(self)
                return result
            end)
        end
        if game:getMoney() ~= FIXED_COIN then
            game.money:setValue(FIXED_COIN)
            refresh(game)
        end
        return 3, game:getMoney()
    end
    if state then
        rawset(peer, 'addGameLayerMoney', state.own)
        rawset(peer, '__carrot4_lock', nil)
        refresh(game)
    end
    return 4, game:getMoney()
end

local function bits(mask, shift)
    if bit then return bit.band(mask, bit.lshift(1, shift)) ~= 0 end
    return math.floor(mask / (2 ^ shift)) % 2 == 1
end

local function money(value)
    local n = tonumber(value) or 0
    n = math.floor(n)
    if n < 0 then n = 0 end
    return n
end

_G.__carrot4_apply = function(mask)
    mask = tonumber(mask) or 0
    coinLock = bits(mask, 0)
    gemLock = bits(mask, 1)
    goldLock = bits(mask, 2)
    shopFree = bits(mask, 3)
    staminaFree = bits(mask, 4)
    lockWallet()
    if staminaFree then
        lockStamina()
    elseif staminaInfinite == 1 then
        unlockStamina()
    end
    if shopFree then
        freeShop()
    else
        shopOpen = 0
    end
    local status, coins = matchCoin(coinLock)
    return status + (money(coins) + 1) * 16
end

_G.__carrot4_wallet = function()
    local list = riches()
    local gold, gem, energy = -1, -1, -1
    if list then
        gold = tonumber(list.gold) or -1
        gem = tonumber(list.gem) or -1
        local model = _data.DataModel
        if model.getPhysicalFromLocal then
            local ok, value = pcall(model.getPhysicalFromLocal, model)
            if ok and type(value) == 'number' then energy = value end
        end
    end
    return gold, gem, energy, shopFixed, shopOpen, staminaInfinite
end

return 1
