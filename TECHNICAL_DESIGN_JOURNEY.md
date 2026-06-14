# Technical Design Journey
There were several iterations of ReaCue. Each one had its limitations and frustrations until I landed on the current 
iteration. This project, like most, was supposed to be a simple project. But it turned into something more complicated
than originally anticipated.

## Phase 1 - I'll just make a simple web proxy
The inspiration came from frustrations when practicing with my band. We were using the built-in "More Me" Web Interface
provided by Reaper. It worked fairly well, but there were some problems:
1. Switching projects could lead to bandmates accidentally changing the wrong parameters.
2. Our practice space did not have Wi-Fi, so I had to create a Wi-Fi hotspot with my phone and look up the IP address and send that to each band mate to connect to.
3. Due to the power hungry Wi-Fi hotspot, my phone's battery would drain and if anyone tried to look something up on their own cell phone, all network traffic would go through my hotspot.

Over time, I became more frustrated and began to think that I could make something better that solved all of these 
issues and made controlling our IEMs a seamless experience. 

My first thought was to create a BLE proxy application that simply converted all normal Reaper Web Interface traffic
into BLE packets. I was even initially planning on simply embedding the existing Reaper Web Interface HTML into the
application, but as I looked into how the Reaper Web Interface works I realized it was not going to work. 

Reaper's web interface uses a pull architecture via polling using semi traditional HTTP requests. It polls at a rate of 
about 10 times a second and the packets are text based and would not scale well for BLE. It would be a lot of traffic
to proxy for 5 simultaneous BLE connections (one for each band member). So I started looking into alternatives.

## Phase 2 - OSC and bespoke UI
After some research, I realized Reaper also supports OSC. OSC is a fairly lightweight UDP protocol that is fast and
allows listening to and changing most of the values I needed. If I used OSC though, I was going to need to create a 
bespoke application UI and forgo my hopeful HTML solution. So I got to work learning how to assemble and parse
OSC messages. OSC is an interesting protocol that is fairly simple once you read the specification carefully.

As I built the UI and started getting all the proper messages from OSC, I started running into issues:
1. Clients have to explicitly set how many tracks/sends/receives to listen to.
2. Project changes spam a lot of packets at once
3. There is no way to determine what packets are a result of a project change or a simple volume change
4. Different projects have different numbers of tracks and there isn't a way to query all tracks in OSC.
5. There is no way to get the project name.
6. There is no way to get/set mute values for receives.
7. OSC float volume values are normalized. I wanted to be able to show dB without having to implement the volume strings (which do come back as dB, but they don't follow the user-configured fader curve when attaching them to my UI). 

With all of those limitations I realized I was going to have to use a two part solution.

## Phase 3 - OSC and the Web API
Using the Web API and OSC in conjunction I was able to successfully query all tracks, receives, .etc for their initial
values and then use OSC to listen for updates. This was the best of both worlds and got me passed several of the issues
mentioned in phase 2, but there were still some problems:
1. Still no way to get the project name
2. Still no way to reliably detect project changes
3. Still no way to get dB and have the sliders accurately follow the fader curve set in Reaper

## Phase 4 - OSC + Web API + Lua script
It was at this point I needed to add yet another piece to the architecture. ReaScript allows you to query the current 
project as well as user settings (the fader curve, min dB, and max dB). So I got to work learning Lua and writing a new
script. My main goal was to send down the project name, fader settings, and detect project changes. I was able to
accomplish all of that by reading the project state (project name and fader values) and checking for changes every tick.
I was hoping I could send an OSC command via Lua, but that is not possible with stock Lua in Reaper. This is supported
by using ReaPack, but I did not want users to have to install ReaPack. So I resorted to sending the values to EXT state 
when they changed. Reaper allows reading EXT state from the Web API natively. Then the apps would poll that EXT state 
once a second. This worked very well, but alas, a sidequest!

## Phase 4.5 - Reverse Engineering Reaper's Fader Curves
I now had access to the fader values, but I needed a way to convert normalized values to dB based on the fader curve.
In ReaScript, Reaper provides functions for doing these conversions, but I unfortunately didn't have access to those 
functions in the mobile applications. It was not going to work to send a value up just to get a dB representation of it
every time I needed it. So I had to figure out how to do it all locally.

A few of Reaper's fader curves are very basic and follow a common formula (linear dB in particular). But the default
formula was not something I knew about. Luckily, AI is better at math than I could ever hope to be. I fed it several
logs spanning the range of the different curve settings while also adjusting the min and max dB parameters. After some
trial and error it was able to figure everything out. Math is not my specialty, so I was happy to let AI handle that.

With that issue solved, I was now able to show all of my values in dB but still use the normalized slider behavior.
This also allowed me to show the 0dB tick in the correct place regardless of what settings users have in Reaper. Things
were looking up, and I was pretty happy with this solution. I was mildly annoyed that the user was going to have to
configure three different things to get all of this working (web interface, osc, and Lua script), but it was a one-time
setup, and I was ok with that. That is, until I had my next practice and tried to add a track to the active project.
Adding, removing, or removing tracks messed the whole application up because Reaper is index based and I didn't have
a clean way to detect when a track moved without matching on name or constantly polling. The main problem was OSC would
spam the update track index packets before any EXT state polling told me about the track position changes.

## Phase 5 - EEL2 ReaScript
I was originally looking for a way to uniquely identify tracks so I could track additions/deletions/moves, and then 
realized Reaper assigns GUIDs to tracks. That was going to be really helpful assuming I could get the GUIds down from 
Lua land. It was around this time I realized a small note in the ReaScript documentation that EEL2 (another language
for writing ReaScript and JsFx) added TCP support. That single line changed everything. If I was able to send my own 
data down a TCP socket, I could handle all the track management at the ReaScript level and only send the changes down 
I needed. So I got to work replacing a large majority of what I had done with the final implementation. I had to learn 
EEL2 which is a really unique scripting language. It was actually fairly fun to work with. It has a lot of constraints 
and I don't particularly like the deeply nested conditionals because there are no early returns, but overall it was a 
fun challenge. If it had better IDE support it would have been much nicer to work with.

By implementing everything in EEL I was able to improve things greatly:
1. The user only needs to install a single script
2. I have access to all parameters I need (and more) in ReaScript
3. I can send whatever byte structure or messages I want
4. Project change notifications are instantaneous instead of polling
5. I can instantly detect if a track is added/removed/or moved and instantly send a message down
6. It allows for a true push architecture instead of mixing push with pull.

With all of those wins, I ended up not even needing to send GUIDs down to the applications. Because I am in control
of the messages (and don't have to worry about stray OSC packets messing things up), I can detect a move in ReaScript
(which does use the track's GUID) and then send down a full update of the project structure very quickly.

With this implementation, I have done more than I initially set out to do. I was trying to avoid having to write my
own change detection and TCP socket implementation, but the builtin tools just weren't doing what I needed. I'm glad
that Reaper has such a flexible and fast scripting ecosystem to allow for some off-the-wall implementations.

# Other Design Choices
## BLE
BLE was really the only option to do what I needed. It's commonly available on phones and tablets, and it's available on
my MacBook which is what I run my practices through. There were some other intentional decisions I made in how I
implemented BLE:

### No Pairing/Bonding
I thought about using bonding, but ultimately I was nervous of someone attempting to bond to the peripheral during a
live show and having a bonding dialog pop up on my MacBook. Apple doesn't allow you to change any of those settings.

### No Encryption
There's nothing private about the data being sent, so encryption wasn't really necessary, and it would only complicate 
things since I wasn't able to use BLE's builtin encryption via bonding/pairing. That being said, I didn't
want anyone to be able to connect without somehow validating they should connect.

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
all use the same interface. I'm quite pleased and it is working quite well.