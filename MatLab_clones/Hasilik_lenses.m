% See emails from Andrej Hasilik regarding construction of 
% systems of lens shaped objects in the unit disk, June 2024.
% This is an attempt to interpret what he told me about his 
% construction, though I'm not sure I have it right.

% Set n and draw unit circle
n=20;
figure;
hold off;
show_circle([0,1]);
hold on;

% flags: what to show
plotlenses=true;
plotRhombi=false;

% Locate 2n+2 equally spaced points on the unit circle.
% These are the tip points of the lenses.
tips=zeros(2*n+2,2); % (x,y) of tips on unit circle
diags=zeros(n); 
del=pi/(n+1);
for j=1:2*n+2
    tips(j,1)=cos(j*del);
    tips(j,2)=sin(j*del);
    % debug: circles at tips
    % show_circle([(tips(j,1)+tips(j,2)*1i),.05]);
end

% draw the diagonal segments; these forn the rhombi
t=linspace(0,1,100);
for j=1:2*n
    J=2*n+2-j+1;
    x=tips(j,1)+t*(tips(J,1)-tips(j,1)); % down from left to right
    y=tips(j,2)+t*(tips(J,2)-tips(j,2));
    if plotRhombi
        plot(x,y);
    end
    J=2*n+2-j-1;
    x=tips(j,1)+t*(tips(J,1)-tips(j,1)); % down from right to left
    y=tips(j,2)+t*(tips(J,2)-tips(j,2));
    if plotRhombi
         plot(x,y);
    end
end
% including the last segment
j=2*n+2;
J=2*n+1;
x=tips(j,1)+t*(tips(J,1)-tips(j,1));
y=tips(j,2)+t*(tips(J,2)-tips(j,2));
if plotRhombi
    plot(x,y);
end

% Find the intersection forming right vertex of each rhombus.
% First at 1; for last lens we also need the left vertex at -1.
r_ends=zeros(1,n+1);
r_ends(1)=1.0;
r_ends(n+1)=-1.0;
for j=2:n
    s=tips(j,2)/(tips(j,2)+tips(j-1,2));
    r_ends(j)=tips(j,1)+s*(tips(j-1,1)-tips(j,1));
    % debug: circles at intersection points
    % show_circle([r_ends(j),.025]);
end

% Find the distances from the vertical diagonal to the right
% corner of the rhombus, Xr, and to the left corner, Xl.
Xr=zeros(1,n);
Xl=zeros(1,n);
for j=1:n
    Xr(j)=r_ends(j)-tips(j,1);;
    Xl(j)=tips(j,1)-r_ends(j+1);
end

% Find the radius of circlular arc through the right 
% corner, Rr, and through the left corner, Rl. Use these
% to get the half angle measures of the arcs, Ar and Al.
Rr=zeros(1,n);
Rl=zeros(1,n);
Ar=zeros(1,n);
Al=zeros(1,n);
for j=1:n
    x=Xr(j);
    Rr(j)=(tips(j,2)^2+x^2)/(2*x);
    Ar(j)=asin(tips(j,2)/Rr(j));
    x=Xl(j);
    Rl(j)=(tips(j,2)^2+x^2)/(2*x);
    Al(j)=asin(tips(j,2)/Rl(j));
end

% draw the lenses
for j=1:n
    % through right corner
    z=tips(j,1)+Xr(j)-Rr(j);
    if plotlenses
        show_arc(z,Rr(j),-Ar(j),Ar(j));
    end
    % through left corner
    z=tips(j,1)-Xl(j)+Rl(j);
    if plotlenses
        show_arc(z,Rl(j),pi-Al(j),pi+Al(j));
    end
end