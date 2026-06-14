# Technical Design Journey
ReaCue started as a simple concept and then turned into a deeper learning experience than I expected. There have been
several iterations of ReaCue; each one requiring learning something new and having its limitations and frustrations. 
This is the story of how ReaCue came to be.

## Phase 0 - Reaper's builtin More Me web interface
The inspiration for ReacCue came from frustrations when practicing with my band. We were using the built-in "More Me" 
Web Interface provided by Reaper. It worked fairly well, but there were some problems:

#### Error-Prone Behavior When Switching Projects
Switching projects could lead to bandmates accidentally changing volume on someone else's track if they forgot to
refresh the page. The web interface only uses track indices to know which parameter get updated. If a track is moved, 
added, or removed the indices often changed leading to confusing behavior and people messing up each other's mixes.

#### Wi-Fi Required
Using the web interface required being on the same Wi-Fi network. Unfortunately our practice space did not have 
Wi-Fi, so I ended up having to creating a Wi-Fi hotspot with my phone and send the random IP address it assigned to
my laptop and send that to each bandmate to connect to.

#### Battery Drain and Cell Data Usage
Due to the power hungry Wi-Fi hotspot, my phone's battery would drain and if someone tried to look something up on 
their phone, all network traffic would go through my hotspot adding to my data usage. In addition, a cell phone is not
a very performant router which could lead to a lot of dropped packets.

Over time, I became more frustrated and began to think that I could make something better that solved all of these 
issues allowing us to have a seamless IEM mixing experience. I set off with the following goals:
1. Support adjusting the hardware output volume
2. Support adjusting all receives' volume
3. Support muting receives
4. Support panning receives
5. Show the current levels in dB
6. Show the current project name
7. Gracefully handle switching projects

## Phase 1 - I'll just make a simple web proxy
My first thought was to create a proxy application that simply converted all normal Reaper Web Interface traffic
into BLE packets. The plan was to embed the web interface HTML in an app that changed the implementation of all the 
Reaper functions to convert them to BLE to send to a server application that would then convert it back to the
Reaper web API commands. However, as I looked into how the Reaper Web Interface works I realized it was not going to be
the right solution. 

#### Pull Architecture
Reaper's web interface uses a pull architecture via polling using traditional HTTP requests. It polls at a rate of 
about 10 times a second, meaning that the BLE radio was going to be very chatty even if nothing was changing. Having
five people using the same application was going to make the BLE radio space even more chatty. I wanted the apps to be
reactive instead of polling 10 times a second per client.

#### Text Based
The simple proxy was going to send the data as is, but the messages contained more information than I needed and 
generally in a more bloated text format.

Due to these limitations, I went back to the drawing board.

## Phase 2 - OSC and bespoke UI
After some research, I realized Reaper also supports OSC. OSC is a fairly lightweight UDP protocol that is fast and
allows listening to and changing most of the values I needed. If I used OSC though, I was going to need to create a 
bespoke application UI and forgo my hopeful HTML solution. So I got to work learning how to assemble and parse
OSC messages. OSC is an interesting protocol that is fairly simple once you read the specification carefully, but there
were some definite gotchas that slowed me down a bit (namely the way everything is aligned to 4 bytes).

As I built the UI and started getting all the proper messages from OSC, I started running into issues:
1. Clients have to explicitly set how many tracks/sends/receives to listen to.
2. Project changes spam a lot of packets at once. All parameters of all tracks are sent down as individual messages.
3. There is no way to determine what packets are a result of a project change or a simple volume change.
4. Different projects have different numbers of tracks and there isn't a way to query the track number in OSC. You have
  to know the track number ahead of time and subscribe to the proper number of tracks, otherwise you'll get ghost track
  data.
5. There is no way to get the project name in OSC.
6. There is no way to get/set mute values for receives OSC. 
7. Adding/removing/moving tracks still broke the experience.
8. There is no way to get the dB values with OSC and know what the min/max and fader curve is.

With all of those limitations I realized I was going to have to use a two part solution.

## Phase 3 - OSC and the Web API
Using the Web API and OSC in conjunction I was able to successfully query all tracks, receives, etc. for their initial
values and then use OSC to listen for updates. This was the best of both worlds and got me passed several of the issues
mentioned in phase 2, but there were still some problems:
1. No way to get the project name
2. No way to reliably detect project changes
3. No way to handle track moves/adds/removes
4. No way to show dB and have the sliders accurately follow the user-defined fader curve/min/max set in Reaper

## Phase 4 - OSC + Web API + Lua script
It was at this point I needed to add yet another piece to the architecture. ReaScript allows you to query the current 
project as well as user settings (the fader curve, min dB, and max dB). So I got to work learning Lua and writing a new
script. My main goal was to send down the project name, fader settings, and detect project changes. I was able to
accomplish all of that by reading the project state (project name and fader values) and checking for changes every tick.
I was hoping I could send an OSC command via Lua, but that is not possible with stock Lua in Reaper. This is supported
by using ReaPack, but I did not want users to have to install ReaPack. So I resorted to sending the values to EXT state 
when they changed. Reaper allows reading EXT state from the Web API natively, so I was able to have the apps poll the
EXT state every second (even though I really didn't want polling). This worked pretty well, but I still had some 
remaining issues:
1. No native mute implementation for receives in OSC (both for setting and getting). I would have to use polling and
  EXT state for mute.
2. I needed to figure out how to resolve a normalized slider value to dB and back locally.

## Phase 4.5 - Reverse Engineering Reaper's Fader Curves
I now had access to the fader values, but I needed a way to convert normalized values to dB based on the fader curve.
In ReaScript, Reaper provides functions for doing these conversions, but I unfortunately didn't have access to those 
functions in the mobile applications. It was not going to work to send a value up to Lua just to get a dB representation 
of it every time I needed it. So I had to figure out how to do it all locally.

A few of Reaper's fader curves are straightforward and follow a common formula (linear dB in particular). But the 
default formula was not something I knew about. Luckily, AI is better at math than I could ever hope to be. I fed it 
several logs spanning the range of the different curve settings while also adjusting the min and max dB parameters. 
After some trial and error it was able to figure everything out. Math is not my specialty, so I was happy to let AI 
handle that.

With that issue solved, I was now able to show all of my values in dB but still use the normalized slider behavior.
This also allowed me to show the 0dB tick in the correct place on the sliders regardless of what settings users have 
in Reaper. Things were looking up, and I was pretty happy with this solution. I was annoyed that the user was going to 
have to configure three different things to get all of this working (web interface, OSC, and a Lua script), but it was
a one-time setup, and I was ok with that. That is, until I had my next practice and tried to add a track to the 
active project. Adding, removing, or removing tracks messed the whole application up because I still didn't have a 
clean way to detect when a track moved without caching data in Lua and manually tracking the position myself. I didn't
really want to deal with that. There were some other minor issues but ultimately things were getting messier and I
was not liking the direction things were going. I wanted a way for tracks to have a unique identifier so I could track 
their positions on the app side.

## Phase 5 - EEL2 ReaScript
After some more research (and somehow missing it up to this point), I realized Reaper assigns GUIDs to tracks. I thought
this was going to be it and everything was going to be solved. I could track the GUID and index in Lua and then send
down a project change event if something changed. Just before I was about to implement GUIDs though, I happened upon 
a small note in the ReaScript documentation that changed everything. I saw that there was another language you could 
write ReaScripts in. I also saw that this languages has support for TCP sockets! That single line changed everything. 
From the beginning I wanted to have a direct, streaming, bidirectional data I could control. But for whatever reason, 
I didn't see that it was possible in Reaper. If I had known that from the start, I could have saved myself a lot
of trouble. 

Now that I was able to send my own data down a TCP socket, I could handle all the track management at the ReaScript 
level and only send the changes down I needed. Using this TCP protocol also opened up access to pretty much any Track
value including receive mute. 

So I got to work replacing a large majority of what I had done with the final implementation. I had to learn 
EEL2 which is a unique scripting language. It is actually fairly fun to work with. It has a lot of constraints 
and I don't particularly like the deeply nested conditionals (there are no early returns or any returns for that matter),
but overall it was a fun challenge. If it had better IDE support it would be much nicer to work with.

By implementing everything in EEL2 I was able to improve things greatly:
1. The user only needs to install a single script
2. I have access to all parameters I need (and more) in ReaScript
3. I can send whatever byte structure or messages I want
4. Project change notifications are instantaneous instead of polling
5. I can instantly detect if a track is added/removed/or moved via GUIDs and indices and instantly send a message down
6. It allows for a true push architecture instead of mixing push with pull.

With all of those wins, I ended up not even needing to send GUIDs down to the client application. Because I am in 
control of the messages (and don't have to worry about stray OSC packets messing things up), I can detect a move in ReaScript and then send down a full update of the project structure using only indices very quickly.

With this implementation, I have done more than I initially set out to do. I was trying to avoid having to write my
own change detection and TCP socket implementation, but the builtin tools just weren't doing what I needed. I'm glad
that Reaper has such a flexible and fast scripting ecosystem to allow for some off-the-wall implementations.

# Other Design Choices
## BLE
From a communication standpoint, BLE was really the only option to do what I needed. It's commonly available on 
phones and tablets, and it's available on my MacBook which is what I run my practices through. That being said, there 
were some other design decisions I made in how I implemented BLE:

### No Pairing/Bonding
I thought about using pairing/bonding, but ultimately I was nervous of someone attempting to bond to the peripheral 
during a live show and having a bonding dialog pop up on my MacBook. Apple doesn't allow you to change any of the 
settings that control the peripheral security on MacOS.

### No Encryption
There's nothing private about the data being sent, so encryption isn't really necessary, and it would only complicate 
things since I'm not using BLE's builtin encryption via bonding/pairing. That being said, I didn't want anyone to be 
able to connect without somehow validating they should be able to connect.

### Authentication Handshake
I wanted a way to restrict who could talk to the peripheral So I decided to use a fairly simple method of HMAC hash
comparisons with nonces. The peripheral sends a nonce and the central uses that to hash and send the passcode. The
peripheral than validates and adds the central to an allow-list. The nonce is immediately consumed and cannot be used
again. This was a simple but effective means of authorization. I doubt this will be used in any large-scale environment
where people are actively trying to hack it, but I wanted to at least make an effort.

### CBOR
CBOR is the serialization format used to transmit data on BLE along with a simple custom message protocol for 
notifications. I debated using Protocol Buffers for its superior byte packing, but I ultimately enjoyed the flexibility
CBOR has. It's still a binary format and allows for fairly dense byte packing. It's also natively supported by
kotlinx serialization.

## Same UI for peripheral and central
I also decided to use the same UI for both the central and the peripheral so I could do the same things on the 
peripheral side. This posed an interesting design challenge though. The peripheral has two roles: 
1. Advertise and manage all bluetooth connections and data
2. Listen to and proxy all communication to Reaper

I was able to come up with a design that allows all three roles (central BLE, peripheral BLE, and peripheral TCP) to
all use the same interface. I'm quite pleased with it and it is working quite well.