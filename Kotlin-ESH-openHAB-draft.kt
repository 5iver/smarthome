// Rule, compact version

rule "My Wakeup" {
    triggers-when { item["BedroomLight"].is(ON) && currentTime.isAfter(SUNRISE + 30.minutes) }
    dont-retrigger-for { 23.hours }
    actions {
        item["BedroomLight"].cmd(OFF)
        thing["Radio1"].channel["power"].cmd(ON)
        thing["Radio1"].channel["volume"].cmd(60.percent)
        thing["Radio1"].channel["station"].cmd("AltRock2")
    }
}

// Rule, advanced version, could be in same or different file
rule "My Kotlin Rule1" {
    //optional. 
    // this weekly scheduling engine is implemented already
    // TODO: monthly and yearly schedule support
    enabled-at { 
        days(MONDAY..FRIDAY) {
            during 1530.mt..MIDNIGHT // mt means military time, 00:00 to 23:59
            during MIDNIGHT..SUNRISE
        }
        day(SATURDAY)
    }
    // there could be multiple enabled-at clauses
    
    //optional
    forbidden-at { 
        day(SUNDAY) {
            during 630.mt..NOON
            during 1530.mt..SUNSET
        }
        day(WEDNESDAY)
    }
    // there could be multiple forbidden-at clauses
    // forbidden-at takes priority over enabled-at
    
    // if current time is outside enabled-at and forbidden-at, should the rule be enabled?
    enabled-by-default { false }
    
    // optional. how long before rule is allowed to execute again.
    // default 3.seconds
    dont-retrigger-for { 30.minutes }
    
    // periodically trigger rule, if not already trigged by trigger-when conditions
    // honors forbidden-when and suppress-when conditions
    // uncommented here since doesn't make sense for this demo use-case of intrusion detection
    // retriggers-every { 2.hours }
    // retriggers-every { SUNDAY.at(NOON) }
    // retriggers-every { SUNDAY.at(1530.mt) } // mt means military time, 00:00 to 23:59 hours
    // retriggers-every { SUNRISE+30.minutes }
    // there could be multiple  retriggers-every clauses
    
    //optional aliases for site specific mappings and readability
    aliases {
        "Light1" is-item "very_very_long_item_name1"
        "Light2" is-item "very_very_long_item_name2"
        "Door1"  is-channel "very:very:long:channel:uid1"
        "Motion1" is-channel "very:very:long:channel:uid2"
        "MotionSensor1" is-channel "very:very:long:thing:uid1"
    )
    // there could be multiple alias clauses
    
    //required
    // you may refer to item or channel by special maps called item and channel
    // or both of them by a special map called device
    triggers-when { thing["MotionSensor1"].is(OFFLINE) && !item["Light1"].is(ON) &&
        channel["Door1"].goes(from = CLOSED, to = OPEN) 
    }
    //optionally you may use addinal triggers-when clauses. rule triggers if at least one clause is satisfied
    
    // optional suppress-when clauses to not trigger rule when certain conditions are met.
    // they take priority over trigger-when clauses
    // rule does not trigger if at least one clause is satisfied
    suppress-when { device["Door1"].is(CLOSED) && device["Motion1"].is(OPEN) }
    
    // optional
    // continue when thing, item, channel not found or not ready. similar to bash's set -e
    continue-on-errors { true }
    
    actions {
        // actions go here. free form Kotlin script, with IDE autocomplete,
        // and some nice helpers available in the context
        val msg = "Intrusion alert, suspicious activity near ${device["Door1"].label}";
        // use a predefined function from standard ESH Kotlin extension
        sendUiNotification(msg)

        // actions on items channels things
        // device means ESH Item or Channel, thing means ESH Thing
        device["Light1"].cmd(ON)
        device["Light2"].cmd(ON)

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
common-offline-test-setup {
    // thing, item, channel are maps of respective types, created automatically by framework
    add-test-things {
        TestThing("MotionSensor1", "binding2:gateway1:motion:MotionSensor1")
    }
    
    add-test-items {
        TestItem("Light1", OnOffType)
        TestItem("Light2", OnOffType)
    }
    
    add-test-channels {
        TestChannel("Door1", OpenClosedType)
    }
}

offline-test "Scenario1" {
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

offline-test "Scenario2" {
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
