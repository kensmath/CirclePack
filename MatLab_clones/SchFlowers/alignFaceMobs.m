function [b2f,b2g,sfg] = alignFaceMobs(trif,trig)
% alignFaceMobs(trif,trig): get aligned face Mobius
%   Given three tangency points for each of neighboring 
%   faces f, g, find the maps mf:BE->f and mg:BE->g
%   from BE (base equilaterals, left and right, 
%   respectively) which align so they map 1 to the 
%   common tangency point. Intrinsic schwarzian sfg is
%   sfg=inv(b2g)*b2f(2,1)

%   First task is to find foff, goff, respective indices 
%   of the shared tangency point. 

% Some constants
omega=-1/2+1i*sqrt(3)/2; % cube root of 1
omega2=-1/2-1i*sqrt(3)/2;

% for prerotations, depending on index
prerot0=[1.0,0.0;0.0,1.0]; 
prerot1=[omega,0.0;0.0,1.0];
prerot2=[omega2,0.0;0.0,1.0];
findx=-1;
gindx=-1;
for j=1:3
    for k=1:3
        if abs(trif(j)-trig(k))<.01
            findx=j;
            gindx=k;
        end
    end
end

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

% schwarzian f,g
sch_fg=inv(b2g)*b2f;
sch_fg=sch_fg/sqrt(det(sch_fg));
sfg=sch_fg(2,1);
if trace(sfg)<0 % trace should be +2
    sfg=-1*sfg;
end

end