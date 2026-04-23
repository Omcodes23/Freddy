# 🧠 FreddyPerception AI Module

## 📋 Overview

The **FreddyPerception** module is an observation-only AI subsystem that converts Minecraft's complex game state into a clean, LLM-friendly data structure. It follows a strict separation of concerns:

- **OBSERVE ONLY** - No actions or side effects
- **No Bukkit objects in output** - Only primitives and simple collections
- **JSON-serializable** - Ready for LLM integration
- **Query support** - Targeted perception for specific blocks or mobs

## 🧱 Module Architecture

```
FreddyPerception  ← Observes and translates game world
     ↓
FreddyWorldState  ← JSON / structured data (LLM-friendly)
     ↓
LLM (future)      ← Reasons about the world
     ↓
Decision          ← Action commands
```

## 📦 Core Components

### 1. FreddyWorldState.java
**Pure data class** containing Freddy's perception of the world.

**Categories:**
- **Position**: x, y, z, biome
- **Player Context**: nearby players, distance, health
- **Threats**: hostile mobs, danger assessment
- **Environment**: lava, water, obsidian detection
- **Resources**: nearby ores
- **Inventory**: obsidian count, tools
- **Safety**: ground stability, lava below
- **Query Results**: targeted search results
- **Meta**: tick time

### 2. FreddyQuery.java
**Optional query object** for targeted perception.

**Features:**
- `blockType`: Find specific block types (e.g., "LAVA", "DIAMOND_ORE")
- `mobType`: Find specific mobs (e.g., "ZOMBIE", "SKELETON")
- `radius`: Search radius (default: 6 blocks)
- `nearestOnly`: Return only the nearest match

### 3. FreddyPerception.java
**Main perception engine** that observes the world.

**Methods:**
```java
// Basic observation
FreddyWorldState observe()

// Observation with query
FreddyWorldState observe(FreddyQuery query)
```

### 4. FreddyPerceptionExample.java
**Usage examples and integration patterns** (12 examples included).

## 🚀 Quick Start

### Basic Usage

```java
import com.freddy.plugin.ai.*;
import net.citizensnpcs.api.npc.NPC;

// Initialize
NPC freddy = /* your NPC instance */;
FreddyPerception perception = new FreddyPerception(freddy);

// Observe the world
FreddyWorldState state = perception.observe();

// Check conditions
if (state.threatNearby) {
    // React to threats
}

if (state.obsidianCount >= 10) {
    // Build portal
}
```

### Query Usage

```java
// Find nearest lava
FreddyQuery query = new FreddyQuery();
query.blockType = "LAVA";
query.radius = 12;

FreddyWorldState state = perception.observe(query);

if (state.querySatisfied) {
    System.out.println("Found lava at: " + 
        state.queryX + ", " + state.queryY + ", " + state.queryZ);
    System.out.println("Distance: " + state.queryDistance);
}

// Find any mob type - supports ALL Minecraft entities!
query.mobType = "HORSE";  // or COW, PIG, VILLAGER, etc.
query.radius = 20;
state = perception.observe(query);
```

### In-Game Testing

```
/freddy-observe                    # Basic world scan
/freddy-observe horse              # Find nearest horse
/freddy-observe cow 25             # Find cow within 25 blocks
/freddy-observe lava               # Find lava
/freddy-observe diamond_ore        # Find diamond ore
/freddy-observe zombie             # Find zombie

# Supports ALL Minecraft entity types and materials!
# Examples: pig, sheep, villager, creeper, enderman, iron_ore, etc.
```

### JSON Serialization (for LLM)

```java
import com.google.gson.Gson;

Gson gson = new Gson();
FreddyWorldState state = perception.observe();

// Convert to JSON
String json = gson.toJson(state);

// Send to LLM (future integration)
// String decision = LLM.ask(json);
```

## 📊 Example World State JSON

```json
{
  "x": 123.5,
  "y": 64.0,
  "z": -88.2,
  "biome": "PLAINS",
  "playerNearby": true,
  "nearestPlayer": "Steve",
  "playerDistance": 5.2,
  "playerHealth": 20.0,
  "threatNearby": true,
  "nearbyHostileMobs": ["ZOMBIE", "SKELETON"],
  "lavaNearby": true,
  "waterNearby": false,
  "obsidianNearby": false,
  "nearbyOres": ["IRON_ORE", "COAL_ORE"],
  "obsidianCount": 7,
  "hasDiamondPickaxe": true,
  "hasWaterBucket": true,
  "standingOnSolidBlock": true,
  "lavaBelow": false,
  "querySatisfied": false,
  "tickTime": 1735632000000
}
```

## 🎯 Use Cases

### Portal Building
```java
// Check for existing obsidian
FreddyQuery query = new FreddyQuery();
query.blockType = "OBSIDIAN";
query.radius = 15;

FreddyWorldState state = perception.observe(query);

if (state.querySatisfied) {
    // Mine existing obsidian
} else if (state.lavaNearby && state.hasWaterBucket) {
    // Create new obsidian
}
```

### Combat / Protection Mode
```java
FreddyQuery query = new FreddyQuery();
query.mobType = "ZOMBIE";
query.radius = 8;

FreddyWorldState state = perception.observe(query);

if (state.querySatisfied && state.queryDistance < 5) {
    // Engage threat
}
```

### Mining
```java
FreddyQuery query = new FreddyQuery();
query.blockType = "DIAMOND_ORE";
query.radius = 10;

FreddyWorldState state = perception.observe(query);

if (state.querySatisfied) {
    // Navigate to ore
}
```

## 🔄 Integration with Existing Code

### In FreddyPlugin.java

```java
private FreddyPerception perception;

private void loadFreddyNPC() {
    freddy = /* ... */;
    
    // Initialize perception
    perception = new FreddyPerception(freddy);
}

public FreddyPerception getPerception() {
    return perception;
}
```

### In FreddyMovement.java (or other AI modules)

```java
// Import
import com.freddy.plugin.ai.*;

// Use perception
FreddyPerception perception = plugin.getPerception();
FreddyWorldState state = perception.observe();

// Make decisions based on world state
if (state.threatNearby) {
    startProtectMode();
}
```

## 🧪 Testing

The module compiles successfully with:
```bash
mvn clean compile -pl minecraft-plugin -am
```

## 🔮 Future Integration

### Phase 1: Current (Manual Decision Making)
```java
FreddyWorldState state = perception.observe();

if (state.threatNearby) {
    action = "defend";
} else if (state.nearbyOres.size() > 0) {
    action = "mine";
}
```

### Phase 2: LLM Integration (Planned)
```java
FreddyWorldState state = perception.observe();
String json = gson.toJson(state);

// Send to LLM
String decision = LLMConnector.ask(json);

// Parse and execute
FreddyActions.execute(decision);
```

## 📚 API Reference

### FreddyPerception

| Method | Parameters | Returns | Description |
|--------|-----------|---------|-------------|
| `observe()` | - | `FreddyWorldState` | Basic world observation |
| `observe(query)` | `FreddyQuery` | `FreddyWorldState` | Targeted observation with query |

### FreddyQuery

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `blockType` | `String` | `null` | Block type to search for |
| `mobType` | `String` | `null` | Mob type to search for |
| `radius` | `int` | `6` | Search radius in blocks |
| `nearestOnly` | `boolean` | `true` | Only return nearest match |

### FreddyWorldState

See the [Example World State JSON](#-example-world-state-json) section for all fields.

## ✅ Design Principles

1. **Separation of Concerns**: Perception is separate from decision-making and actions
2. **LLM-First Design**: All data structures are JSON-serializable primitives
3. **No Side Effects**: Observation never modifies game state
4. **Extensible**: Easy to add new perception fields as needed
5. **Query Support**: Targeted perception for specific needs

## 🎉 Status

✅ All classes implemented  
✅ Builds successfully  
✅ Gson dependency added  
✅ Example usage provided  
✅ Ready for integration  

## 📝 Next Steps

1. **Integrate** with existing FreddyPlugin
2. **Add** perception to AI decision loops
3. **Test** in-game with real scenarios
4. **Extend** with additional perception fields as needed
5. **Connect** to LLM for intelligent decision-making

---

**Created**: 2025-12-31  
**Author**: Freddy AI Team  
**Status**: Production Ready
