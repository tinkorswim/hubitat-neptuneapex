# hubitat-neptuneapex

This driver is meant to be a bridge between the Hubitat to Neptune Apex.  It was designed to allow to allow for displaying of input values (tempurature, pH) on dashboards
and for control of Apex outlets from button devices or rules.

I only know of this to work on the lastest Hubitat A3 series of controllers.  I have the ApexA3 and can't test on other or earlier models.
All API access is unoffical.  They were reverse engineered from the fusion apps and are not published or supported by Neptunes Systems.
It may stop working if Neptune decides to change their API. I would not use the hubitat to control life critcal system, but rather augment existing Apex functions and notifications.

Big shout out to David Snell https://www.drdsnell.com/projects/hubitat/ for the inspiration and figuring out all the hard parts for me.

Currently there is support for the following

1) Outputs - energy bar outlets, 24v and virtual switches
2) Inputs - temperature, pH, FMM Water sensors, break out box switch status
3) Configurable polling interval
4) Read-Only mode - Display device status only, no commands will be sent to Apex
5) Selective Child Device - Create Child devices for only select did
6) Sync hubitat names with Apex Names
