% Script for confirming computations of schwarzians for a
% euclidean flower

% Some constants: base equilateral is {1, omega, omega2}
sq3=sqrt(3);
omega=-1/2+sq3/2*1i; % cube root of 1
omega2=-1/2-sq3/2*1i;
base1=1+sq3*1i; % base equilateral circles, radius sq3
base2=-2;
base3=1-sq3*1i;
base4=4;

% for prerotations, depending on index
prerot0=[1.0,0.0;0.0,1.0]; 
prerot1=[omega,0.0;0.0,1.0];
prerot2=[omega2,0.0;0.0,1.0];
findx=-1;

% Mobius of base equilateral to 0, 1, infty
faceflip=[-1.0, 2.0;0.0, 1.0]; % rotate base g to base equilateral
b2z1i=z1i(1,omega,omega2);

% read euclidean flower data, row vectors 'rads' and 'cents' 
clear rads;
clear cents;
folder='C:\Users\kensm\Documents\GitHub\CirclePack\MatLab_clones\SchFlowers';
file = fullfile(folder, 'flowerdata_5b.m');
run(file); % Execute the script

% flower degree
sz=size(rads);
deg=sz(2)-1;
n=deg+1;
centcent= cents(n); % data of the central circle
radrad=rads(n);

% close up the data vectors
rads(n)=rads(1); 
cents(n)=cents(1);

% form triples of tangency points of faces {j,J+1,n}
tangPts=zeros(deg,3);
for j=1:deg
    z=centcent; % the center of the flower
    z1=cents(j);
    z2=cents(j+1);
    r=radrad;
    r1=rads(j);
    r2=rads(j+1);
    tangPts(j,1)=eucl_tangency(z,z1,r,r1);
    tangPts(j,2)=eucl_tangency(z1,z2,r1,r2);
    tangPts(j,3)=eucl_tangency(z2,z,r2,r);
end

% Compute edge schwarzians
schwarzians=zeros(1,deg);
mf=inv(z1i(tangPts(1,1),tangPts(1,2),tangPts(1,3)))*b2z1i;

% debug
hold off;
[z,r]=Mob_of_Cir(mf,base1,sq3);
show_circle([z,r]);
hold on;
[z,r]=Mob_of_Cir(mf,base2,sq3);
show_circle([z,r]);
[z,r]=Mob_of_Cir(mf,base3,sq3);
show_circle([z,r]);

mginv=faceflip*inv(b2z1i)*z1i(tangPts(deg,3),tangPts(deg,1),tangPts(deg,2));
m=mginv*mf;
m=m/sqrt(det(m));
if (m(1,1)+m(2,2))<0
    m=-1*m;
end
schwarzians(1)=m(2,1);

for j=2:deg
    mf=inv(z1i(tangPts(j,1),tangPts(j,2),tangPts(j,3)))*b2z1i;

    hold on;
    [z,r]=Mob_of_Cir(mf,base1,sq3);
    show_circle([z,r]);
    [z,r]=Mob_of_Cir(mf,base2,sq3);
    show_circle([z,r]);
    [z,r]=Mob_of_Cir(mf,base3,sq3);
    show_circle([z,r]);

    mginv=faceflip*inv(b2z1i)*z1i(tangPts(j-1,3),tangPts(j-1,1),tangPts(j-1,2));
    m=mginv*mf;
    m=m/sqrt(det(m));
    if (m(1,1)+m(2,2))<0
        m=-1*m;
    end
    schwarzians(j)=m(2,1);
end

schwarzians