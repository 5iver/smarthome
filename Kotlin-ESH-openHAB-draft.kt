// Rule, simplest version
// texts after // are comments
rule "My Wakeup" {
    triggerWhen { "Bedroom1 Lamp".is(ON) && currentTime.isAfter(SUNRISE + 30.minutes) }
    dontRetriggerFor { 23.hours }
    actions {
        // You may use any of: Thing Label, Thing UID, Item name, Channel UID, Thing Label.channelName
        // system figures out what you mean. This intelligent dispatcher is already implemented
        turn "BedroomLight" ON // BedroomLight is an item
        turn "Bedroom1 Lamp" OFF // Bedroom1 Lamp is thing label, see below how dispatch works
        // Internet Radio1 is thing label, power is channel. If multiple things are found, this won't do anything
        // command goes to Internet Radio1.power channel:
        // if power is only channel that accepts OnOffType or is tagged as default channel
        turn "Internet Radio1" ON
        turn "Internet Radio1" 60.percent // channel volume inferred automatically based on data type
        turn "Internet Radio1.station" "AltRock2" // explicit channel specification
    }
}

// Rule, advanced version, could be in same or different file
rule "My Kotlin Rule1" {
    //optional. 
    // this weekly scheduling engine is implemented already
    // TODO: monthly and yearly schedule support
    enabledAt { 
        days(MONDAY..FRIDAY) {
            during 1530.mt..MIDNIGHT // mt means military time, 00:00 to 23:59
            during MIDNIGHT..SUNRISE
        }
        day(SATURDAY)
    }
    // there could be multiple enabledAt clauses
    
    //optional
    forbiddenAt { 
        day(SUNDAY) {
            during 630.mt..NOON
            during 1530.mt..SUNSET
        }
        day(WEDNESDAY)
    }
    // there could be multiple forbidden-at clauses
    // forbiddenAt takes priority over enabledAt
    
    // if current time is outside enabled-at and forbidden-at, should the rule be enabled?
    enabledByDefault { false }
    
    // optional. how long before rule is allowed to execute again.
    // default 3.seconds
    dontRetriggerFor { 30.minutes }
    
    // periodically trigger rule, if not already trigged by trigger-when conditions
    // honors forbiddenWhen and suppressWhen conditions
    // uncommented here since doesn't make sense for this demo use-case of intrusion detection
    // retriggerEvery { 2.hours }
    // retriggerEvery { SUNDAY.at(NOON) }
    // retriggerEvery { SUNDAY.at(1530.mt) } // mt means military time, 00:00 to 23:59 hours
    // retriggerEvery { SUNRISE+30.minutes }
    // there could be multiple  retriggerEvery clauses
    
    //optional aliases for site specific mappings and readability
    aliases {
        "Light1" isItem "very_very_long_item_name1"
        "Light2" isItem "very_very_long_item_name2"
        "Door1"  isChannel "very:very:long:channel:uid1"
        "Motion1" isChannel "very:very:long:channel:uid2"
        "MotionSensor1" isChannel "very:very:long:thing:uid1"
    )
    // there could be multiple alias clauses
    
    //required
    // you may refer to item or channel by special maps called item and channel
    // or both of them by a special map called device
    triggerWhen { "MotionSensor1".is(OFFLINE) && !"Light1".is(ON) &&
        "Door1".goesFrom(CLOSED, OPEN) 
    }
    //optionally you may use addinal triggerWhen clauses. rule triggers if at least one clause is satisfied
    
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
        "Light1".cmd(ON)
        "Light2".cmd(ON)

        // handle collection based actions
        // sendSMS is Kotlin extension, defined in standard lib or by user lib on Java type such as Person
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
    // thing, item, channel are maps of respective types, created automatically by framework
    addTestThings {
        TestThing("MotionSensor1", "binding2:gateway1:motion:MotionSensor1")
    }
    
    addTestItems {
        TestItem("Light1", OnOffType::class)
        TestItem("Light2", OnOffType::class)
    }
    
    addTestChannels {
        TestChannel("Door1", OpenClosedType::class)
    }
}

offlineTest "Scenario1" {
    // test specific setup script. executed after common-offline-test-setup
    setup {
        println("Running Scenario1")
    }
    
    // test main body
    actions {
        thing["MotionSensor1"].updateStatus(ThingStatus.ONLINE)
        item["Light1"].updateState(OnOffType.ON)
        channel["Door1"].updateState(OpenClosedType.CLOSED)
    }
    // there could be multiple actions clauses
    
    // test assertions
    assert { 
        rule["My Kotlin Rule1"].isNotTriggered && rule["My Kotlin Rule2"]?.isNotTriggered 
    }
    // there could be multiple assert clauses
}

offlineTest "Scenario2" {
    setup {
        println("Running Scenario2")
    }
    
    actions {
        thing["MotionSensor1"].updateStatus(OFFLINE)
        item["Light1"].updateState(ON)
        channel["Door1"].updateState(CLOSED)
        delay(0.5f)
        channel["Door1"].updateState(OPEN)
    }
    
    assert {
        rule["My Kotlin Rule1"].isTriggered && device["Light2"].is(ON)
    }
    
    assert { rule["My Kotlin Rule2"]?.isNotTriggered }
}
