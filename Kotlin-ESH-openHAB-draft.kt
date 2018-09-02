// Rule, simplest version
// texts after // are comments
// this syntax is not exaustive, many examples and documentation coming soon
rule "My Wakeup" {
    triggerWhen { "Bedroom1 Lamp".is(ON) && currentTime.isAfter(SUNRISE + 30.minutes) }
    dontRetriggerWithin { 23.hours }
    actions {
        // You may use any of: Thing Label, Thing UID, Item name, Channel UID, Thing Label.channelName
        // system figures out what you mean. This intelligent dispatcher is already implemented
        // setTo and sendCommand are same. you can invoke in traditional function style or Kotlin extention style
        "BedroomLight".setTo(ON) // BedroomLight is an item
        sendCommand("Bedroom1 Lamp", OFF) // Bedroom1 Lamp is thing label, see below how dispatch works
        // Internet Radio1 is thing label. 
        // If multiple candidate things/channels are found, this won't do anything
        // command goes to Internet Radio1's power channel if:
        //      power is only channel that accepts OnOffType OR 
        //      power is tagged as default/catchall channel
        setTo(ON, "Internet Radio1")
        // channel volume inferred automatically based on data type
        "Internet Radio1".setTo(60.percent) 
        // explicit channel specification
        "Internet Radio1.station".sendCommand("AltRock2") 
    }
}

// Rule, advanced version, could be in same or different file
rule "My Kotlin Rule1" {
    //optional. 
    // this weekly scheduling engine is implemented already
    // TODO: monthly and yearly schedule support
    enabledAt { 
        days(MONDAY..FRIDAY) {
            during(1530.mt..MIDNIGHT) // mt means military time, 00:00 to 23:59
            during(MIDNIGHT..SUNRISE)
        }
        day(SATURDAY)
    }
    // there could be multiple enabledAt clauses
    
    //optional
    forbiddenAt { 
        day(SUNDAY) {
            during(630.mt..NOON)
            during(1530.mt..SUNSET)
        }
        day(WEDNESDAY)
    }
    // there could be multiple forbiddenAt clauses
    // forbiddenAt takes priority over enabledAt
    
    // if current time is outside enabledAt and forbiddenAt, should the rule be enabled?
    enabledByDefault { false }
    
    // optional. how long before rule is allowed to execute again.
    // default 3.seconds
    dontRetriggerWithin { 30.minutes }
    
    // periodically trigger rule, if not already trigged by triggerWhen conditions
    // honors forbiddenAt and suppressWhen conditions
    // uncommented here since doesn't make sense for this demo use case of intrusion detection
    // retriggerEvery { 2.hours }
    // retriggerEvery { SUNDAY.at(NOON) }
    // retriggerEvery { SUNDAY.at(1530.mt) } // mt means military time, 00:00 to 23:59 hours
    // retriggerEvery { SUNRISE+30.minutes }
    // there could be multiple  retriggerEvery clauses
    
    //optional aliases for site specific mappings and readability
    aliases {
        "Light1".aliasToItem("very_very_long_item_name1")
        aliasToItem("Light2", "very_very_long_item_name2")
        "Door1".aliasToChannel("very:very:long:channel:uid1")
        aliasToChannel("Motion1", "very:very:long:channel:uid2")
        "MotionSensor1".aliasToThing("very:very:long:thing:uid1")
        aliasToThing("FrontMotion", "very:very:long:thing:uid1")
        // multiple aliases can point to same target device
    }
    // there could be multiple aliases clauses
    
    // optional
    triggerWhen { "MotionSensor1".status(OFFLINE) && !"Light1".is(ON) &&
        "Door1".goesFrom(CLOSED, OPEN) 
    }
    //there could be multiple triggerWhen clauses. rule triggers if at least one clause is satisfied
    // other usages: "Outdoor Light".receivesCommand(ON) || "Outdoor Light".receivesUpdate(OFF) 
    //               "Outdoor Light".statusGoes(OFFLINE)
    
    // optional suppressWhen clauses to not trigger rule when certain conditions are met.
    // they take priority over trigger-when clauses
    // rule does not trigger if at least one clause is satisfied
    suppressWhen { "Door1".is(CLOSED) && "Motion1".is(OPEN) }
    
    // optional
    // continue when thing, item, channel not found or not ready. similar to bash's set -e
    continueOnErrors { true }
    
    actions {
        // actions go here. free form Kotlin script, with IDE autocomplete,
        // and some nice helpers available in the context
        val msg = "Intrusion alert, suspicious activity near ${device["Door1"].label}";
        // use a predefined function from standard ESH Kotlin extension
        sendUiNotification(msg)

        // actions on items channels things
        // device means ESH Item or Channel, thing means ESH Thing
        "Light1".setTo(ON) // one way of sending command
        sendCommand("Light2", ON) // yet another way of sending command

        // handle collection based actions
        systemConfig.emergencyPersonal.filter(it.name == "Jack" || it.name == "Kim").forEach { sendSMS(it.phone, msg) }

        // lookup and use OSGI service, with special systemService helper
        val jsonStore = systemService<StorageService>()
        var myStorage: Storage<Int> = jsonStore.getStorage("MyStore")
        var alertCount? = myStorage.get("AlertCount")

        // null safety made easy
        // if old alertCount found, increment it, else initialize with 1
        myStorage.put("AlertCount", alertCount?++:1) 
    }
    
    // there could be multiple actions clauses
}


// common setup, executed freshly before every test
commonOfflineTestSetup {
    "MotionSensor1".addAsTestThing("binding2:gateway1:motion:MotionSensor1")
    "Light1".addAsTestItem(OnOffType::class)
    addAsTestItem("Light2", OnOffType::class)
    "Door1".addAsTestChannel(OpenClosedType::class)
}

offlineTest "Scenario1" {
    // test specific setup script. executed after common-offline-test-setup
    setup {
        println("Running Scenario1")
    }
    
    // test main body
    actions {
        "MotionSensor1".updateStatus(ONLINE)
        "Light1".updateState(ON)
        "Door1".updateState(CLOSED)
    }
    // there could be multiple actions clauses
    
    // test assertions
    assert { 
        "My Kotlin Rule1".isNotTriggered && "My Kotlin Rule2".isNotTriggered 
    }
    // there could be multiple assert clauses
}

offlineTest "Scenario2" {
    setup {
        "Running Scenario2".println()
    }
    
    actions {
        "MotionSensor1".updateStatus(OFFLINE)
        "Light1".turn(ON)
        "Door1".updateState(CLOSED)
        delay(0.5f)
        "Door1".updateState(OPEN)
    }
    
    assert {
        "My Kotlin Rule1".isTriggered && "Light2".is(ON)
    }
    
    assert { "My Kotlin Rule2".isNotTriggered }
}
