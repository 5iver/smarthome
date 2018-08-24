#!/usr/bin/env python

"""\
dial-or-sms: dial a list of numbers and playback a .wav file or send SMS

Pythoon script that makes a voice call and plays audio file over GSM modem.
Analog audio is converted to MIC level signals using two resitors (10Kohm and 100 ohm). SIM900 doesn't support AUX line levels.
It uses the dial() methods callback mechanism to be informed when the call is answered and ended.

"""
from __future__ import print_function
from gsmmodem.modem import GsmModem, SentSms
from gsmmodem.exceptions import TimeoutException, PinRequiredError, IncorrectPinError, InterruptedException, CommandError
import sys, time, logging
import logging.config
from pydub import AudioSegment
from pydub.utils import get_player_name, make_chunks
import pyaudio
from serial.serialutil import SerialException, portNotOpenError, writeTimeoutError

PORT = '/dev/ttyAMA0'
BAUDRATE = 9600
NUMBER = '00000' # Number to dial - CHANGE THIS TO A REAL NUMBER
PIN = None # SIM card PIN (if any)
SMSTEXT = None
waitForCallback = True
AUDIOFILE = None
wav_audio = None
p = None
stream = None
chunks = None
rootLogger = None
def parseArgs():
    """ Argument parser for Python 2.7 and above """
    from argparse import ArgumentParser
    parser = ArgumentParser(description='Script for sending SMS and calling a list of phone numbers')
    parser.add_argument('-i', '--port', metavar='PORT', default="/dev/ttyAMA0", help='port to which the GSM modem is connected; a number or a device name.')
    parser.add_argument('-b', '--baud', metavar='BAUDRATE', default=9600, help='set baud rate')
    parser.add_argument('-p', '--pin', metavar='PIN', default=None, help='SIM card PIN')
    parser.add_argument('-s', '--smstext', metavar='SMSTEXT', default=None, help='Enables SMS and specifies SMS text')
    parser.add_argument('-a', '--audiofile', metavar='AUDIOFILE', help='enables audio call and specifies audio file path in wav format')    
    parser.add_argument('destination', metavar='DESTINATION', help='comma separated list of destination mobile number')
    return parser.parse_args()

def callStatusCallback(call):
    global waitForCallback
    global wav_audio
    global rootLogger
    rootLogger.info('Call status update callback function called')
    global chunks, stream
    # break audio into half-second chunks (to allows keyboard interrupts)
    if call.answered:
        rootLogger.info('Call has been answered; waiting a while...')
        # Wait for a bit - some older modems struggle to send DTMF tone immediately after answering a call
        #time.sleep(3.0)
        rootLogger.info('Playing audio file...')
        try:
            #if call.active: # Call could have been ended by remote party while we waited in the time.sleep() call
            #    call.sendDtmfTone('9515999955951')
            # playback.play(wav_audio)
            for chunk in chunks:
                if call.active:
                    stream.write(chunk._data)
        except InterruptedException as e:
            # Call was ended during playback
            rootLogger.info('Playback interrupted: {0} ({1} Error {2})'.format(e, e.cause.type, e.cause.code))
        except CommandError as e:
            rootLogger.info('Playback failed: {0}'.format(e))
        finally:
            if call.active: # Call is still active
                rootLogger.info('Hanging up call...')
                call.hangup()
    else:
        # Call is no longer active (remote party ended it)
        rootLogger.info('Call has been ended by remote party')
        waitForCallback = False

def main():
    global AUDIOFILE
    args = parseArgs()
    PORT = args.port
    NUMBER = args.destination
    BAUDRATE = args.baud
    PIN = args.pin
    AUDIOFILE = args.audiofile
    SMSTEXT = args.smstext
    if NUMBER == None or NUMBER == '00000':
        print('Error: Please change the NUMBER variable\'s value before running this example.')
        sys.exit(1)
    global wav_audio, p, stream, chunks
    wav_audio = AudioSegment.from_file(AUDIOFILE, format="wav")
    p = pyaudio.PyAudio()
    stream = p.open(format=p.get_format_from_width(wav_audio.sample_width),  
                    channels=wav_audio.channels,
                    rate=wav_audio.frame_rate,
                output=True)
    chunks = make_chunks(wav_audio, 500)
    logging.basicConfig(format='%(name)s: %(message)s', level=logging.DEBUG)
    #logging.config.fileConfig('dial2logging.conf')
    global rootLogger
    rootLogger = logging.getLogger('')
    rootLogger.setLevel(logging.DEBUG)
    formatter = logging.Formatter('%(asctime)s %(name)s: %(message)s')
    fileHandler = logging.FileHandler('C:\Python27\Scripts\dial-sms2.log')
    fileHandler.setFormatter(formatter)
    rootLogger.addHandler(fileHandler)
    consoleHandler = logging.StreamHandler()
    consoleHandler.setFormatter(formatter)
    rootLogger.addHandler(consoleHandler)
    rootLogger.info('Initializing modem...')
    modem = GsmModem(PORT, BAUDRATE)
    retry = True
    connected = False
    retryCount = 0
    retryDelay = 5
    retryMax = 20
    while retry:
        try:
            retry = False
            modem.connect(PIN)
            connected = True
            # disable cell broadcast messages
            modem.write('AT+CSCB=0')
            # set MIC gain level 0-15
            modem.write('AT+CMIC=0,10')
        except SerialException:
            retryCount += 1
            if retryCount <= retryMax:
                retry = True
                rootLogger.info('Serial port unavailable. Waiting for serial port...')
                time.sleep(retryDelay)
    if connected == False:
        rootLogger.error('Could not connect to modem via serial port {0}'.format(PORT))
        sys.exit(1)
    rootLogger.info('Waiting for network coverage...')
    modem.waitForNetworkCoverage(30)
    cnt = 0
    try:
        # temporarily disable all incoming sms nofitications, sms delivery reports and cell broadcast notifications
        modem.write('AT+CNMI=0,0,0,0,0')
        if SMSTEXT != None:
            modem.smsTextMode = True
            for mdn in NUMBER.split(','):
                cnt+=1
                if cnt>1:
                    time.sleep(2)
                rootLogger.info('Sending SMS to : {0}'.format(mdn))
                try:
                    modem.sendSms(mdn, SMSTEXT, waitForDeliveryReport=False)
                except CommandError as e:
                    rootLogger.error('Failed to send message: CommandError {0}'.format(e))
                except TimeoutException:
                    rootLogger.error('Failed to send message: the send operation timed out')
        if AUDIOFILE != None:
            # let +CDS messages clean up
            time.sleep(5)
            cnt = 0
            for mdn in NUMBER.split(','):
                cnt+=1
                if cnt>1:
                    time.sleep(5)
                rootLogger.info('Dialing number: {0}'.format(mdn))
                global waitForCallback
                waitForCallback = True
                try:
                    call = modem.dial(mdn, 60, callStatusUpdateCallbackFunc=callStatusCallback)
                    while waitForCallback:
                        time.sleep(0.1)
                except CommandError as e:
                    rootLogger.info('Failed to call: CommandError {0}'.format(e))
                except TimeoutException:
                    rootLogger.info('Failed to call: the operation timed out')
    finally:
        modem.write('AT+CNMI=2,1,0,0,0') # Incoming SMS notifications, no SMS delivery reports, no cell broadcast notifications
        # modem.write('AT+CNMI=2,1,0,1,0') # Incoming SMS notifications, SMS delivery reports, no cell broadcast notifications
        # modem.write('AT+CNMI=2,1,2,1,0') # Incoming SMS notifications, SMS delivery reports, cell broadcast notifications
    rootLogger.info('Done')
    
if __name__ == '__main__':
    main()
