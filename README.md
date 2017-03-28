# NYC Subway RT shim

This program processes MTA Subway GTFS-RT feeds, converts them to standard GTFS-RT, and publishes a consolidated
TripUpdate feed at a new endpoint. If possible, TripUpdates from the RT feed are matched to static GTFS trips and 
RT IDs are replaced by matched static trip IDs.

### Usage

Build jar:

    mvn package

Get NYC Subway GTFS from MTA developer downloads (login through [mta.info/developers](http://web.mta.info/developers) 
and sign the agreement first):

    wget http://web.mta.info/developers/data/nyct/subway/google_transit.zip

Run the program:

    java -jar nyct-rt-proxy-1.0-SNAPSHOT-withAllDependencies.jar --config config.txt

### config.txt example

Here is an example `config.txt`. Uncomment the `cloudwatch.*` values to send metrics to Cloudwatch (disabled otherwise):

    NYCT.gtfsPath=/path/to/google_transit.zip
    NYCT.key=
    NYCT.latencyLimit=300  # ignore feed if its timestamp is more than 300s in the past
    tripUpdates.url=http://localhost:8001/tripUpdates
    #cloudwatch.namespace=
    #cloudwatch.accessKey=
    #cloudwatch.secretKey=

### Known issues

- There can be duplicate trip IDs in the published feed, because TripUpdates without an exact origin-departure time match are matched
to the closest static trip in the past. We cannot remove duplicates without adding state to the matching algorithm.

### Feed/route reference

See MTA's [list of feeds](http://datamine.mta.info/list-of-feeds).

|Feed ID|Routes|Notes|
|-------|------|-----|
|1|1,2,3,4,5,6,GS|May include a subway path identifier at the end of trip IDs.|
|2|L||
|11|SI||
|16|N,Q,R,W|Intermittent connections.|
|21|B,D|Southbound trips on D may be separated at mid-line relief points.|

Routes in feeds 11, 16, and 21 require "fixup" ie start date must be reformatted and directions must be added to stop IDs.
