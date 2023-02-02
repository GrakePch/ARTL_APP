# ARTL APP

The mobile application for project ARTL. **Augmented Real-time Translating Lens (ARTL)** is a senior project starting from Fall 2022, and expected to be finished at the end of Winter 2023.

| Team Member        | Position        |
| ----               | --------        |
| Raymond O Klefstad | Project Advisor |
| Yiming He          | Hardware Design |
| Jiawei Li          | Hardware Design |
| Jiarui Bi          | Chassis Design  |
| Chenghao Peng      | Software Design |

## Motivation

A real-time translating tool is always necessary for people living in a multilingual society. Typically, people use an e-dictionary or translating app based on a smartphone or computer, but it could be faster and more convenient. We create ARTL, which can be installed on glasses to implement image capture, real-time translation, and text display.

## Project Goals

We plan to build a smart glasses that can capture an image, extract words or phrases from the image, and display translated word on a transparent screen.

By the end of fall quarter, we will build a fully functional conceptual model, powered by an Raspberry Pi 4B, and used for function realization.

By the end of winter quarter, we will replace the 4B with Raspberry Pi Pico Zero, and design a dedicated case with transparent display in it.

## Methodology

 - The camera on the glasses takes the picture in front of the user.
 - The Raspberry Pi sends the photo to a connected android phone.
 - The phone utilizes OCR and Translation API to extract texts and translate them to the target language.
 - The phone sends the text back to the glasses and the Raspberry Pi displays it onto an OLED panel.
 - The target language can be customized  in the phone app.
 
## Milestones

### What we achieved

 - Able to extract words from a image.
 - Able to fetch translated result from Google translation.
 
### What we plan to do next

 - Install camera and take picture according to command.
 - Replace current recognition engine to increase efficiency.
 
## References

https://link.springer.com/chapter/10.1007/978-3-642-14932-0_31

https://www.atlantis-press.com/proceedings/sdmc-21/125968668
