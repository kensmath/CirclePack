% Study of the 7-flower from CirclePack script 'branched7.cps'

s3=sqrt(3);
w=-.5+1i*s3/2;
ww=-.5-1i*s3/2;
id=[1,0;0,1];

% CirclePack data on flower is in this function, which 
% computes the triples of tangency points for the 7 faces
% and then calls 'alignFaceMobs.m' to compute the uzians.
% Recall, a uzian u=1-s where s is a schwarzian.
[c,r,tangpts,intuzians,ju]=seven_example();

% Layout original flower to see if things look okay.
%origFlower=figure;
%hold off
%drawCircle(id,c(8),r(8));
%hold on
%for j=1:7
%    drawCircle(id,c(j),r(j));
%end

% Step by step layout of normalized flower
nc=zeros(7,1);
nr=zeros(7,1);
tgts=zeros(7,1);
normalized=figure;
hold on

% petal 1
nc(1)=0.0-1i;
nr(1)=1.0;
rsqr=1.0;
tgts(1)=0.0;
drawCircle(id,nc(1),nr(1));

% petal 2
[delta,rsqr]=compTarg(2,intuzians(1),1.0,rsqr);
tgts(2)=delta
nr(2)=1/(rsqr*rsqr)
nc(2)=delta-1i*nr(2)
drawCircle(id,nc(2),nr(2))

% petal 3
[delta,rsqr]=compTarg(3,intuzians(2),1/sqrt(nr(1)),rsqr);
tgts(3)=tgts(2)+delta
nr(3)=1/(rsqr*rsqr)
nc(3)=tgts(3)-1i*nr(3)
drawCircle(id,nc(3),nr(3))

% petal 4; this one goes left
[delta,rsqr]=compTarg(3,intuzians(3),1/sqrt(nr(2)),rsqr);
tgts(4)=tgts(3)+delta
nr(4)=1/(rsqr*rsqr)
nc(4)=tgts(4)-1i*nr(4)
drawCircle(id,nc(4),nr(4))

% petal 5; sqr<0, but generic computation still applies
[delta,rsqr]=compTarg(3,intuzians(4),1/sqrt(nr(3)),rsqr);
tgts(5)=tgts(4)+delta
nr(5)=1/(rsqr*rsqr)
nc(5)=tgts(5)-1i*nr(5)
drawCircle(id,nc(5),nr(5))

% petal 6; last petal normally forced to be radius 1

% Problem here: sqr is negative
rsqr=-1*rsqr;

[delta,rsqr]=compTarg(3,intuzians(5),1/sqrt(nr(4)),rsqr);
tgts(6)=tgts(5)+delta
nr(6)=1/(rsqr*rsqr)
nc(6)=tgts(6)-1i*nr(6)
drawCircle(id,nc(6),nr(6))

% calculate u_5
u5=(sqrt(nr(5))+sqrt(nr(5)/nr(4)))/s3


