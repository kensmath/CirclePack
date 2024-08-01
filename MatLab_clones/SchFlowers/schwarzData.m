function [sfg,sFG,sigma,divdiv] = schwarzData(trif,trig,triF,triG)
% schwarzData(trif,trig,triF,triG): get Schwraz Data
%   Given three tangency points for each of neighboring 
%   faces f, g and F,G, find the intrinsic schwarzians
%   sfg and sFG, the Schwarzian Derivative, sigma, and the 
%   derivative of the Mobius from BE, the base equilateral 
%   to f. Along the way, we find the Mobius maps (adjusted
%   for proper index): mf: f -> F, mg: g -> G, and baseMob's
%   bmf: BE -> f, bmg: BE -> g, bmF: BE -> F, and bmG: BE -> G. 
%
%   First task is to find foff, goff, Foff, and Goff. 
%   These are respective indices of the shared tangency 
%   points. 

% Some constants
omega=-1/2+sqrt(3)/2*1i; % cube root of 1
omega2=-1/2-sqrt(3)/2*1i;

% for prerotations, depending on index
prerot0=[1.0,0.0;0.0,1.0]; 
prerot1=[omega,0.0;0.0,1.0];
prerot2=[omega2,0.0;0.0,1.0];
findx=-1;
gindx=-1;
Findx=-1;
Gindx=-1;
for j=1:3
    for k=1:3
        if abs(trif(j)-trig(k))<.01
            findx=j;
            gindx=k;
        end
        if abs(triF(j)-triG(k))<.01
            Findx=j;
            Gindx=k;
        end
    end
end

% Mobius maps mf: f -> F, mg: g -> G.
tmp1=z1i(trif(findx),trif(1+mod(findx,3)),trif(1+mod(findx+1,3)));
tmp2=z1i(triF(Findx),triF(1+mod(Findx,3)),triF(1+mod(Findx+1,3)));
mf=inv(tmp2)*tmp1;
mf=mf/sqrt(det(mf))

tmp1=z1i(trig(gindx),trig(1+mod(gindx,3)),trig(1+mod(gindx+1,3)));
tmp2=z1i(triG(Gindx),triG(1+mod(Gindx,3)),triG(1+mod(Gindx+1,3)));
mg=inv(tmp2)*tmp1;
mg=mg/sqrt(det(mg))

% find Schwarzian Derivative, matrix and comlex number
SchDiv=inv(mg)*mf;
SchDiv=SchDiv/sqrt(det(SchDiv));
sigma=SchDiv(2,1)

% find base equilateral maps: not adjusted based on index.
base2z1i=z1i(1,omega,omega2); % base equilateral to 0,1,infty
base2z1i/sqrt(det(base2z1i));
rota1=[-1.0,2.0;0.0,1.0]; % rotate by pi around 1.
rota1=rota1/sqrt(det(rota1));

% maps FROM base equilateral to face
dmf=inv(z1i(trif(1),trif(2),trif(3)))*base2z1i;
dmf=dmf/sqrt(det(dmf));
dmg=inv(z1i(trig(1),trig(2),trig(3)))*base2z1i;
dmg=dmg/sqrt(det(dmg));
rmF=inv(z1i(triF(1),triF(2),triF(3)))*base2z1i;
rmF=rmF/sqrt(det(rmF));
rmG=inv(z1i(triG(1),triG(2),triG(3)))*base2z1i;
rmG=rmG/sqrt(det(rmG));

% intrinsic schwarzians; adjust for shared edges
b2f=dmf;
if findx==2
    b2f=b2f*prerot1;
elseif findx==3
    b2f=b2f*prerot2;
end
b2f=b2f/sqrt(det(b2f));

b2g=dmg;
if gindx==2
    b2g=b2g*prerot1;
elseif gindx==3
    b2g=b2g*prerot2;
end
b2g=b2g*rota1;
b2g=b2g/sqrt(det(b2g));

b2F=rmF;
if Findx==2
    b2F=b2F*prerot1;
elseif Findx==3
    b2F=b2F*prerot2;
end
b2F=b2F/sqrt(det(b2F));

b2G=rmG;
if Gindx==2
    b2G=b2G*prerot1;
elseif Gindx==3
    b2G=b2G*prerot2;
end
b2G=b2G*rota1;
b2G=b2G/sqrt(det(b2G));

% schwarzian f,g
sch_fg=inv(b2g)*b2f;
sch_fg=sch_fg/sqrt(det(sch_fg));
sfg=sch_fg(2,1);

% schwarzian F,G
sch_FG=inv(b2G)*b2F;
sch_FG=sch_FG/sqrt(det(sch_FG));
sFG=sch_FG(2,1);

% derivative of b2f
divdiv=1/((b2f(2,1)+b2f(2,2))^2);

% check computations:
checkup=sfg+sigma*divdiv-sFG;

end