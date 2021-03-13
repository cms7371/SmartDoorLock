# SmartDoorLock 성균관대학고 2015311208 채민석 종합설계프로젝트 안드로이드 소스 코드
## Main Activity 바로가기 : https://github.com/cms7371/SmartDoorLock/blob/master/app/src/main/java/com/smartdoorlock/MainActivity.java
#### 종합설계프로젝트의 주제인 무선전력송신장치를 적용하여 배터리 교체가 없는 스마트 도어락의 소스코드 입니다. 레포트 내용 일부를 발췌하여 자세한 설명을 대신합니다.
> 과제 추진배경(개발동기) 및 목적, 필요성
> 우리가 일상생활에서 마주치는 기존의 도어락과 새롭게 등장하는 스마트 도어락은 주기적으로 건전지를 교체해 주면서 사용해야 한다. 하지만 이러한 도어락에 무선 전력 송수신 장치를 접목해, 문이 닫혔을 때 전원이 공급되면서 배터리가 충전되고, 문이 열리는 동안에는 내장된 배터리로 동작하도록 설계해 도어락의 배터리를 주기적으로 교체해 주어야하는 불편함을 해소할 수 있다고 생각했다. 
> 또한, 와이파이 통신 기능을 갖추고 있는 라즈베리파이를 이용해 스마트폰과 연동해서 사용할 수 있는 스마트 도어락을 만들기로 결정했다. 라즈베리파이에 마이크와 카메라를 연결해 벨을 눌렀을 때 스마트폰과 와이파이를 이용한 영상 및 음성 통신을 지원한다. 그리고 영상 통신을 위한 카메라를 QR 리더기로 활용해 QR 코드를 통한 문열림 기능을 추가했다. 스마트폰에서 임시 QR 코드를 생성해 게스트에게 전달해주면 게스트는 그 QR 코드를 이용해 집 주인이 없는 경우에도 도어락을 열 수 있으며, 한번 사용한 QR 코드는 라즈베리파이 내에서 삭제되어 다시 사용할 수 없게 된다. 마지막으로 택배 알람 기능을 추가해 택배기사님이 배달 후 초인종이 아닌 택배 알람 버튼을 누르면 스마트폰으로 택배 도착 알람이 전달되는 기능이다.
### 발표 자료 : https://drive.google.com/file/d/1O3RGLhvTz7fjC2eZ58SzzX8I6X1ILFsF/view?usp=sharing
### 최종 레포트 : https://drive.google.com/file/d/1O1WHRaIdk-EtWKZyQCxJw4dQqAxuuIfx/view?usp=sharing
###### 피폐한 정신상태에서 데스크탑-렙탑 간의 공유를 위해 이용한 repository이기에 commit message가 많이 혼란합니다.
